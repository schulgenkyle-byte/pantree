// Mystery Nights — multiplayer game backend.
//
// Each game = one Cloudflare Durable Object instance keyed by a 4-letter code.
// The DO holds: room state, WebSocket connections (host + N players), beat
// script timeline, per-character action history, and an alarm that auto-fires
// the next beat.
//
// Network bounds: a code maps 1:1 to a DO. Joining requires the code. The DO
// only routes messages within its own room. No cross-room leakage.
//
// Message protocol (JSON over WebSocket, both directions):
//
//   Client -> server:
//     { t: "host_create",   menu_id, script }       // host opens room, sends beat script
//     { t: "player_join",   code, name }            // player attaches to existing room
//     { t: "host_begin" }                            // host kicks off the game
//     { t: "host_advance" }                          // host manually fires next beat
//     { t: "host_pause" }
//     { t: "host_resume" }
//     { t: "player_action", action_id, beat_index } // player responds to a twist
//     { t: "ping" }
//
//   Server -> client:
//     { t: "room_created",  code, host_id }
//     { t: "joined",        you, characters_taken } // back to the joining player
//     { t: "lobby_update",  players: [{ id, name, character_id }] }
//     { t: "character_assigned", character_id, name, role, secret } // direct
//     { t: "beat_pushed",   beat_index, beat: {...} }                // direct or broadcast
//     { t: "host_view",     beat_index, beat_label, players_responded } // host only
//     { t: "reveal",        body, killer_character_id, ending_id }
//     { t: "pause" } / { t: "resume" }
//     { t: "error",         message }
//
// The host can also play a character if they want. The DO doesn't care — host
// is a session role (sends host commands), not a character slot.

import { json, err, readJson, uid, sha256Hex } from './util.js';

// ---------------------------------------------------------------------------
//  Code generation. Excludes ambiguous chars (I, O, 0, 1).
// ---------------------------------------------------------------------------
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // 32 chars
function mintCode() {
  let s = '';
  const buf = crypto.getRandomValues(new Uint8Array(4));
  for (let i = 0; i < 4; i++) s += CODE_ALPHABET[buf[i] % CODE_ALPHABET.length];
  return s;
}

// ---------------------------------------------------------------------------
//  HTTP handlers (host-create + WebSocket upgrade gateway).
// ---------------------------------------------------------------------------
export const handleGames = {
  /**
   * POST /games/create  — host endpoint. Mints a code, registers it with the
   * DO, returns the code + WebSocket URL. Host then opens a WebSocket to that
   * URL with the host_create payload.
   *
   * Body: { menu_id: string }
   * Returns: { code, ws_path }
   *
   * Auth: requires a logged-in user (host needs Speakeater account). Player
   * joins are public (no auth) — the code is the credential.
   */
  async create(request, userId, env) {
    const body = await readJson(request, 8_000);
    if (body.error) return body.error;
    const { menu_id } = body.value || {};
    if (typeof menu_id !== 'string' || menu_id.length > 64) {
      return err(400, 'menu_id required');
    }

    // Try up to 5 mints to avoid the rare collision.
    let code = null;
    for (let i = 0; i < 5; i++) {
      const c = mintCode();
      const id = env.GAME_ROOM.idFromName(c);
      const stub = env.GAME_ROOM.get(id);
      const resp = await stub.fetch('https://do.local/reserve', {
        method: 'POST',
        body: JSON.stringify({ code: c, menu_id, host_user_id: userId }),
      });
      if (resp.ok) { code = c; break; }
    }
    if (!code) return err(503, 'could not mint code, try again');

    return json({
      code,
      ws_path: `/games/${code}/ws`,
      // Player join URL convenience. The native app uses the code directly.
      player_url: `https://speakeater.com/play/${code}`,
    }, 201, request, env);
  },

  /**
   * GET /games/:code/ws  — WebSocket upgrade. Routes to the DO for this code.
   *
   * Query: ?role=host&user_id=...  (host, must have created the room)
   *        ?role=player&name=Charlotte (player joining via code)
   */
  async wsUpgrade(request, env, code) {
    if (!/^[A-Z2-9]{4}$/.test(code)) return err(400, 'bad code');
    const id = env.GAME_ROOM.idFromName(code);
    const stub = env.GAME_ROOM.get(id);
    // Forward the WebSocket request straight to the DO. The DO handles the
    // upgrade so it can attach the socket to its in-memory connection set.
    return stub.fetch(request);
  },
};

// ---------------------------------------------------------------------------
//  GameRoom Durable Object.
//
//  One instance per active game room. The instance is alive while at least
//  one WebSocket is connected; alarms keep state warm for auto-fire beats
//  even when the host backgrounds the app briefly.
//
//  State shape (persisted via this.state.storage):
//    code: 'RYAS'
//    menu_id: 'murder_algonquin'
//    host_user_id: 'u_xxx' (creator of the room — for resume auth)
//    phase: 'lobby' | 'playing' | 'paused' | 'reveal' | 'done'
//    script: GameScript (the beat list, sent by host at create time)
//    players: [{ id, name, character_id, ws_session_id }]
//    current_beat: -1 (not started) | 0..N
//    beat_history: [{ beat_index, fired_at, actions: [{ player_id, action_id }] }]
//    started_at: epoch ms
//    paused_at: epoch ms | null
// ---------------------------------------------------------------------------

const HEARTBEAT_MS = 30_000;
const ROOM_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours

export class GameRoom {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    // In-memory map of session_id -> { ws, role, player_id }
    this.sessions = new Map();
  }

  // -------------------------------------------------------------------------
  //  fetch — accepts WebSocket upgrades + admin endpoints (reserve, status).
  // -------------------------------------------------------------------------
  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (path === '/reserve' && request.method === 'POST') {
      return this._reserve(request);
    }

    if (path.endsWith('/ws')) {
      return this._handleWsUpgrade(request, url);
    }

    if (path === '/status') {
      return this._statusJson();
    }

    return new Response('not found', { status: 404 });
  }

  async _reserve(request) {
    const body = await request.json();
    const existing = await this.state.storage.get('code');
    if (existing && existing !== body.code) {
      return new Response('room already in use', { status: 409 });
    }
    if (existing) {
      // Already reserved (idempotent retry).
      return new Response('ok', { status: 200 });
    }
    await this.state.storage.put({
      code: body.code,
      menu_id: body.menu_id,
      host_user_id: body.host_user_id,
      phase: 'lobby',
      players: [],
      current_beat: -1,
      beat_history: [],
      created_at: Date.now(),
    });
    // Schedule garbage-collect alarm in 12 hours.
    await this.state.storage.setAlarm(Date.now() + ROOM_TTL_MS);
    return new Response('ok', { status: 200 });
  }

  async _handleWsUpgrade(request, url) {
    const upgradeHeader = request.headers.get('Upgrade');
    if (!upgradeHeader || upgradeHeader.toLowerCase() !== 'websocket') {
      return new Response('expected websocket', { status: 426 });
    }
    const role = url.searchParams.get('role') || 'player';
    const name = url.searchParams.get('name') || '';
    const userId = url.searchParams.get('user_id') || '';

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];

    const sessionId = uid();
    this.state.acceptWebSocket(server, [role, sessionId, name, userId]);
    this.sessions.set(sessionId, { ws: server, role, name, user_id: userId, player_id: null });

    // Send initial state to the connecting socket.
    await this._sendInitialState(server, role, sessionId, name);

    return new Response(null, { status: 101, webSocket: client });
  }

  // -------------------------------------------------------------------------
  //  Hibernatable WebSocket handlers (called by runtime on each message /
  //  close / error, even if the DO has hibernated between events).
  // -------------------------------------------------------------------------

  async webSocketMessage(ws, msg) {
    let parsed;
    try { parsed = JSON.parse(typeof msg === 'string' ? msg : new TextDecoder().decode(msg)); }
    catch { this._send(ws, { t: 'error', message: 'bad json' }); return; }

    const tags = this.state.getTags(ws); // [role, sessionId, name, userId]
    const role = tags[0];
    const sessionId = tags[1];

    try {
      switch (parsed.t) {
        case 'ping':
          this._send(ws, { t: 'pong' });
          break;

        case 'host_create':
          if (role !== 'host') return this._send(ws, { t: 'error', message: 'not host' });
          await this._hostCreate(ws, sessionId, tags[3], parsed);
          break;

        case 'player_join':
          if (role !== 'player') return this._send(ws, { t: 'error', message: 'not player' });
          await this._playerJoin(ws, sessionId, parsed);
          break;

        case 'host_begin':
          if (role !== 'host') return this._send(ws, { t: 'error', message: 'not host' });
          await this._hostBegin(ws);
          break;

        case 'host_advance':
          if (role !== 'host') return this._send(ws, { t: 'error', message: 'not host' });
          await this._advanceBeat(ws, /* manual */ true);
          break;

        case 'host_pause':
          if (role !== 'host') return this._send(ws, { t: 'error', message: 'not host' });
          await this._pause();
          break;

        case 'host_resume':
          if (role !== 'host') return this._send(ws, { t: 'error', message: 'not host' });
          await this._resume();
          break;

        case 'player_action':
          if (role !== 'player') return this._send(ws, { t: 'error', message: 'not player' });
          await this._playerAction(ws, sessionId, parsed);
          break;

        default:
          this._send(ws, { t: 'error', message: 'unknown message type' });
      }
    } catch (e) {
      this._send(ws, { t: 'error', message: e?.message || 'server error' });
    }
  }

  async webSocketClose(ws, code, reason, wasClean) {
    const tags = this.state.getTags(ws);
    const sessionId = tags[1];
    this.sessions.delete(sessionId);
    // Player leaves visible to host; the DO state stays so the player can
    // reconnect on the same code with the same name and pick up their
    // character. We don't remove the player from state on close.
    await this._broadcastLobby();
  }

  async webSocketError(ws, e) {
    const tags = this.state.getTags(ws);
    this.sessions.delete(tags[1]);
  }

  // -------------------------------------------------------------------------
  //  Alarms — used for auto-fire beats + room GC.
  // -------------------------------------------------------------------------
  async alarm() {
    const phase = await this.state.storage.get('phase');
    const createdAt = await this.state.storage.get('created_at');

    // GC if room has been around too long.
    if (createdAt && Date.now() - createdAt > ROOM_TTL_MS && phase !== 'playing') {
      await this.state.storage.deleteAll();
      return;
    }

    if (phase !== 'playing') return; // Don't auto-fire while lobby / paused / done.

    await this._advanceBeat(null, /* manual */ false);

    const next = await this._nextBeatAlarmMs();
    if (next) await this.state.storage.setAlarm(next);
  }

  // -------------------------------------------------------------------------
  //  Game state mutations.
  // -------------------------------------------------------------------------

  async _sendInitialState(ws, role, sessionId, name) {
    const code = await this.state.storage.get('code');
    if (!code) {
      this._send(ws, { t: 'error', message: 'room not initialized' });
      return;
    }
    this._send(ws, { t: 'connected', role, code });
  }

  async _hostCreate(ws, sessionId, userId, parsed) {
    const phase = await this.state.storage.get('phase');
    if (phase && phase !== 'lobby') {
      // The host is reconnecting to an already-in-progress room. That's fine —
      // just re-send current state.
      await this._broadcastLobby();
      return;
    }
    const hostUserId = await this.state.storage.get('host_user_id');
    if (hostUserId && userId && hostUserId !== userId) {
      return this._send(ws, { t: 'error', message: 'not the original host' });
    }

    const { script } = parsed;
    if (!script || !Array.isArray(script.beats)) {
      return this._send(ws, { t: 'error', message: 'script required' });
    }
    await this.state.storage.put('script', script);

    this._send(ws, { t: 'room_created', code: await this.state.storage.get('code') });
    await this._broadcastLobby();
  }

  async _playerJoin(ws, sessionId, parsed) {
    const players = (await this.state.storage.get('players')) || [];
    const name = String(parsed.name || '').slice(0, 24).trim();
    if (!name) return this._send(ws, { t: 'error', message: 'name required' });

    // If a player with this name is already in the room (reconnect), update
    // their session pointer. Otherwise add them.
    let player = players.find(p => p.name === name);
    if (!player) {
      // Assign next available character_id from the script.
      const script = await this.state.storage.get('script');
      const cast = script?.cast || [];
      const taken = new Set(players.map(p => p.character_id));
      const available = cast.find(c => !taken.has(c.id));
      const charId = available?.id || null;

      player = {
        id: uid(),
        name,
        character_id: charId,
        session_id: sessionId,
        joined_at: Date.now(),
      };
      players.push(player);
      await this.state.storage.put('players', players);

      if (charId && available) {
        this._send(ws, {
          t: 'character_assigned',
          character_id: available.id,
          name: available.name,
          role: available.role,
          secret: available.secret,
          objective: available.objective || '',
          bluffs: Array.isArray(available.bluffs) ? available.bluffs : [],
        });
      }
    } else {
      // Reconnect — refresh session_id and re-send character.
      player.session_id = sessionId;
      await this.state.storage.put('players', players);

      const script = await this.state.storage.get('script');
      const character = script?.cast?.find(c => c.id === player.character_id);
      if (character) {
        this._send(ws, {
          t: 'character_assigned',
          character_id: character.id,
          name: character.name,
          role: character.role,
          secret: character.secret,
          objective: character.objective || '',
          bluffs: Array.isArray(character.bluffs) ? character.bluffs : [],
        });
      }
    }

    // Don't re-accept the WS. Tags are set once at accept time. We route
    // targeted beats by looking up the player by name via storage.

    this._send(ws, { t: 'joined', you: player });
    await this._broadcastLobby();
  }

  async _hostBegin(ws) {
    const script = await this.state.storage.get('script');
    if (!script) return this._send(ws, { t: 'error', message: 'no script' });

    await this.state.storage.put('phase', 'playing');
    await this.state.storage.put('started_at', Date.now());
    await this.state.storage.put('current_beat', -1);

    this._broadcast({ t: 'game_started' });

    // Fire the first beat immediately, then schedule the next.
    await this._advanceBeat(null, /* manual */ false);
    const next = await this._nextBeatAlarmMs();
    if (next) await this.state.storage.setAlarm(next);
  }

  async _advanceBeat(ws, manual) {
    const script = await this.state.storage.get('script');
    if (!script) return;

    let current = await this.state.storage.get('current_beat');
    const next = current + 1;
    if (next >= script.beats.length) {
      // Game ended via natural progression — emit reveal if not already.
      await this.state.storage.put('phase', 'done');
      return;
    }

    const beat = script.beats[next];
    await this.state.storage.put('current_beat', next);

    // Append to beat history for replay.
    const history = (await this.state.storage.get('beat_history')) || [];
    history.push({ beat_index: next, fired_at: Date.now(), actions: [] });
    await this.state.storage.put('beat_history', history);

    // Dispatch the beat.
    if (beat.target === 'all' || !beat.target) {
      this._broadcast({ t: 'beat_pushed', beat_index: next, beat });
    } else {
      // Targeted beat. Target player gets the real beat; every other player
      // gets a different random cover beat fired at the same instant so
      // observers at the table cannot pattern-match whose phone got the
      // actionable info. See memory/project_mystery_nights_design_doctrine.md.
      const players = (await this.state.storage.get('players')) || [];
      const target = players.find(p => p.character_id === beat.target);

      // Optional beats targeted at an absent (un-joined) cast slot get
      // skipped entirely — no real beat, no covers. This is how mysteries
      // support a range of party sizes (e.g. 4-7) without phantom buzzes
      // for an unplayed character.
      if (!target && beat.is_optional) {
        return;
      }

      const library = (Array.isArray(script.cover_beat_library) && script.cover_beat_library.length > 0)
        ? script.cover_beat_library
        : ['A waiter passes the table without making eye contact.'];

      if (target) {
        const targetWs = await this._wsForPlayer(target.id);
        if (targetWs) this._send(targetWs, { t: 'beat_pushed', beat_index: next, beat });
      }

      for (const p of players) {
        if (target && p.id === target.id) continue;
        const coverBody = library[Math.floor(Math.random() * library.length)];
        const coverBeat = {
          time_offset_minutes: beat.time_offset_minutes,
          target: p.character_id,
          kind: 'COVER',
          body: coverBody,
          actions: null,
        };
        const pws = await this._wsForPlayer(p.id);
        if (pws) this._send(pws, { t: 'beat_pushed', beat_index: next, beat: coverBeat });
      }

      // Host always sees the full real beat for the director's view.
      this._sendHost({ t: 'host_view', beat_index: next, beat });
    }

    // If this is a reveal beat, emit a special reveal event to all.
    if (beat.kind === 'REVEAL') {
      this._broadcast({
        t: 'reveal',
        body: beat.body,
        killer_character_id: beat.killer_character_id || null,
        ending_id: beat.ending_id || null,
      });
      await this.state.storage.put('phase', 'reveal');
    }
  }

  async _nextBeatAlarmMs() {
    const script = await this.state.storage.get('script');
    const current = await this.state.storage.get('current_beat');
    const startedAt = await this.state.storage.get('started_at');
    if (!script || !startedAt) return null;
    const nextIdx = current + 1;
    if (nextIdx >= script.beats.length) return null;
    const beat = script.beats[nextIdx];
    return startedAt + (beat.time_offset_minutes || 0) * 60 * 1000;
  }

  async _pause() {
    await this.state.storage.put('phase', 'paused');
    await this.state.storage.put('paused_at', Date.now());
    await this.state.storage.deleteAlarm();
    this._broadcast({ t: 'pause' });
  }

  async _resume() {
    const phase = await this.state.storage.get('phase');
    if (phase !== 'paused') return;
    const pausedAt = await this.state.storage.get('paused_at');
    const startedAt = await this.state.storage.get('started_at');
    const pauseDuration = Date.now() - pausedAt;
    await this.state.storage.put('started_at', startedAt + pauseDuration);
    await this.state.storage.put('phase', 'playing');
    await this.state.storage.delete('paused_at');
    this._broadcast({ t: 'resume' });
    const next = await this._nextBeatAlarmMs();
    if (next) await this.state.storage.setAlarm(next);
  }

  async _playerAction(ws, sessionId, parsed) {
    const { action_id, beat_index } = parsed;
    const history = (await this.state.storage.get('beat_history')) || [];
    const entry = history[beat_index];
    if (entry) {
      const tags = this.state.getTags(ws);
      // Look up the player_id by matching name from tags to stored players.
      const players = (await this.state.storage.get('players')) || [];
      const player = players.find(p => p.name === tags[2]);
      const playerId = player?.id || null;
      entry.actions.push({ player_id: playerId, action_id, at: Date.now() });
      await this.state.storage.put('beat_history', history);
    }
    // Echo back the action to the host (the director's view).
    this._sendHost({ t: 'player_action_recorded', beat_index, action_id });
  }

  // -------------------------------------------------------------------------
  //  Broadcast helpers.
  // -------------------------------------------------------------------------
  async _broadcastLobby() {
    const players = (await this.state.storage.get('players')) || [];
    const code = await this.state.storage.get('code');
    const menu_id = await this.state.storage.get('menu_id');
    const phase = await this.state.storage.get('phase');
    this._broadcast({
      t: 'lobby_update',
      code,
      menu_id,
      phase,
      players: players.map(p => ({
        id: p.id,
        name: p.name,
        character_id: p.character_id,
      })),
    });
  }

  _broadcast(payload) {
    const sockets = this.state.getWebSockets();
    for (const ws of sockets) {
      this._send(ws, payload);
    }
  }

  _sendHost(payload) {
    const sockets = this.state.getWebSockets('host');
    for (const ws of sockets) this._send(ws, payload);
  }

  async _wsForPlayer(playerId) {
    const players = (await this.state.storage.get('players')) || [];
    const player = players.find(p => p.id === playerId);
    if (!player) return null;
    const sockets = this.state.getWebSockets();
    for (const ws of sockets) {
      const tags = this.state.getTags(ws);
      // tags = [role, sessionId, name, userId] — name match identifies the player's socket.
      if (tags[2] === player.name) return ws;
    }
    return null;
  }

  _send(ws, payload) {
    try {
      ws.send(JSON.stringify(payload));
    } catch (_) {
      // ignore — socket likely closed
    }
  }

  async _statusJson() {
    const code = await this.state.storage.get('code');
    const phase = await this.state.storage.get('phase');
    const players = await this.state.storage.get('players');
    const current = await this.state.storage.get('current_beat');
    return new Response(JSON.stringify({ code, phase, current_beat: current, players: players?.length || 0 }), {
      headers: { 'Content-Type': 'application/json' },
    });
  }
}
