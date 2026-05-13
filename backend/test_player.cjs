// Node.js Mystery Nights player tester.
//
//   node test_player.cjs <CODE> [NAME]
//
// Connects to the deployed pantrie-backend GameRoom Durable Object as a
// player, sends player_join with the given name, then prints every server
// message to stdout. Useful for validating the multiplayer flow from a
// computer when the host is on a phone.
//
// Requires the `ws` package — `npm install ws` if missing.

const WebSocket = require('ws');

const code = (process.argv[2] || '').toUpperCase();
const name = process.argv[3] || 'TestPlayer';

if (!/^[A-Z2-9]{4}$/.test(code)) {
  console.error('Usage: node test_player.cjs <CODE> [NAME]');
  console.error('CODE must be 4 letters/digits (no I, O, 0, 1).');
  process.exit(1);
}

const url = `wss://pantrie-backend.schulgenkyle.workers.dev/games/${code}/ws?role=player&name=${encodeURIComponent(name)}`;
console.log(`connecting: ${url}`);

const ws = new WebSocket(url);

ws.on('open', () => {
  console.log('[open] sending player_join');
  ws.send(JSON.stringify({ t: 'player_join', code, name }));
});

ws.on('message', (data) => {
  const text = data.toString();
  let obj;
  try { obj = JSON.parse(text); } catch { console.log('[raw]', text); return; }
  const t = obj.t;
  if (t === 'character_assigned') {
    console.log('\n=== YOUR CHARACTER ===');
    console.log('Name:   ', obj.name);
    console.log('Role:   ', obj.role);
    console.log('Secret: ', obj.secret);
    console.log('======================\n');
  } else if (t === 'beat_pushed') {
    console.log(`\n--- BEAT #${obj.beat_index} (${obj.beat.kind}) T+${obj.beat.time_offset_minutes} ---`);
    console.log(obj.beat.body);
    if (obj.beat.actions) {
      console.log('Actions:');
      obj.beat.actions.forEach((a, i) => console.log(`  ${i + 1}. [${a.id}] ${a.label}`));
      console.log('To respond, type a number 1..N and press enter (or "skip").');
    }
    console.log('-----------\n');
  } else if (t === 'reveal') {
    console.log('\n=== REVEAL ===');
    console.log(obj.body);
    console.log('Killer character_id:', obj.killer_character_id);
    console.log('==============\n');
  } else if (t === 'lobby_update') {
    console.log(`[lobby_update] ${obj.phase} · ${obj.players.length} player(s): ${obj.players.map(p => p.name).join(', ')}`);
  } else if (t === 'error') {
    console.error('[error]', obj.message);
  } else {
    console.log('[' + t + ']', JSON.stringify(obj));
  }
});

ws.on('close', (code, reason) => {
  console.log('[close]', code, reason?.toString());
  process.exit(0);
});

ws.on('error', (err) => {
  console.error('[error]', err.message);
});

// Stdin handler — type a number to respond to the latest twist.
let lastBeatIndex = -1;
let lastActions = null;
ws.on('message', (data) => {
  try {
    const obj = JSON.parse(data.toString());
    if (obj.t === 'beat_pushed' && obj.beat.actions) {
      lastBeatIndex = obj.beat_index;
      lastActions = obj.beat.actions;
    }
  } catch {}
});

process.stdin.on('data', (chunk) => {
  const input = chunk.toString().trim();
  if (input === 'skip' || !lastActions) return;
  const n = parseInt(input, 10);
  if (isNaN(n) || n < 1 || n > lastActions.length) return;
  const action = lastActions[n - 1];
  console.log(`sending player_action: ${action.id}`);
  ws.send(JSON.stringify({ t: 'player_action', beat_index: lastBeatIndex, action_id: action.id }));
});
