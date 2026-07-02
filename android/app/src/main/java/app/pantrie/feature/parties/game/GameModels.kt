package app.pantrie.feature.parties.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire protocol shared with the Cloudflare GameRoom Durable Object.
 * Field names match the JSON keys exactly. See backend/src/games.js for the
 * server-side counterpart.
 */

@Serializable
data class GameScript(
  val total_duration_minutes: Int,
  val cast: List<GameCharacter>,
  val beats: List<GameBeat>,
  /**
   * Period-flavored throwaway lines fired to NON-target players whenever a
   * targeted beat goes out, so every phone vibrates at the same instant and
   * observers cannot pattern-match whose phone got the real twist. See
   * memory/project_mystery_nights_design_doctrine.md.
   */
  val cover_beat_library: List<String> = emptyList(),
  /**
   * Host-only facilitation layer. The host runs the room — they need a
   * monologue, a success picture, panic buttons for stalls, and post-game
   * prompts. Without this the host is flying blind between beats.
   */
  val host_cues: HostCues? = null,
  /**
   * Classifies the reveal so labels like "killer" do not mislead. For the
   * Vanishing Socialite, solution_type = DISAPPEARANCE — there is no
   * murder. UI uses this to label the reveal correctly.
   */
  val solution_type: String = "MURDER",
  /**
   * Privacy doctrine read aloud by the host at T+0 before any other beat
   * fires. Locks the rule: your dossier is yours, your phone is yours,
   * you may lie / bluff / invent, you do not show another guest your
   * screen. Without this, the new physical-movement layer (STEP_AWAY /
   * RECON / RENDEZVOUS) has no rule of trust to lean on.
   */
  val privacy_preamble: String = "",
)

/**
 * Director's-chair material. Lives on the host's phone only.
 */
@Serializable
data class HostCues(
  /** 2-4 sentence opening read aloud by the host. Stage-directed prose. */
  val opening_monologue: String = "",
  /** What "on track" looks like at minute 30 / 60 / 80. Host self-audit. */
  val success_picture: String = "",
  /**
   * Panic buttons the host can fire (verbally) when the table stalls.
   * 1-line prompts ("ask the Matron what she's been quiet about for ten
   * minutes — she has been holding something").
   */
  val panic_buttons: List<String> = emptyList(),
  /** Post-reveal warm-down prompts so the night does not die at T+90. */
  val post_game_prompts: List<String> = emptyList(),
  /** Recommended drinks the host plans for. Period-correct, three max. */
  val recommended_cocktails: List<String> = emptyList(),
)

@Serializable
data class GameCharacter(
  val id: String,            // "the_visiting_author"
  val name: String,          // "The Visiting Author"
  val role: String,          // 2-3 sentence character setup
  val secret: String,        // What only this character knows
  /**
   * The player's agenda for the night. Drives action beyond reactive
   * twist-clicking. Shown on the character card and in the persistent
   * in-game header. 1-2 short sentences.
   */
  val objective: String = "",
  /**
   * Optional cast slot. The mystery plays cleanly without this character —
   * any TWIST beats targeted at them are skipped if no player joined the
   * slot. Lets a mystery support a range of party sizes (e.g. 4-7) instead
   * of a locked exact count. Core cast must be non-optional so the reveal
   * always lands.
   */
  val is_optional: Boolean = false,
  /**
   * For the killer only — pre-written cover stories the player can deploy
   * verbatim when challenged. Blood on the Clocktower's bluff list. The
   * killer is doing the hardest improv at the table; the script gives
   * them this scaffolding.
   */
  val bluffs: List<String> = emptyList(),
)

@Serializable
data class GameBeat(
  /** Minutes since game start when this beat fires. 0 = immediately on Begin. */
  val time_offset_minutes: Int,
  /** "all" for broadcast, or a character.id for targeted dispatch. */
  val target: String,
  /**
   * Information-layer kinds:
   *   NARRATIVE | TWIST | REVEAL | SYSTEM | COVER | CLOCK
   *
   * Physical-movement kinds (audit Part 4 — turn the off-stage spaces into
   * a real game layer; create suspicion through absence at the table):
   *   STEP_AWAY   — leave the table for N minutes. Empty chair = suspicion.
   *   RECON       — STEP_AWAY with a covert objective.
   *   GATHER_INTEL — retrieve a physical prop without being asked why.
   *   RENDEZVOUS  — paired beat to two phones at the same instant: meet alone.
   *   SNEAK       — physical action performed in plain sight (slip a pin, swap a card).
   *   DEAD_DROP   — leave/retrieve a physical object in a fixed room location.
   *   BE_SEEN     — cinematic eye-contact moment, slow walk past a chair.
   *   MISSED      — broadcast that one named player is currently absent — forces table to notice.
   *
   * CLOCK is mechanically a SYSTEM broadcast. Movement kinds are
   * mechanically TWIST-style (targeted) — the dedicated kinds exist so
   * the UI can render them with movement-specific chrome (timer, prop
   * note, "go" button).
   */
  val kind: String,
  /** The on-screen body of the beat. */
  val body: String,
  /** For TWIST beats: the multiple-choice options the player picks from. */
  val actions: List<GameBeatAction>? = null,
  /** Reveal-only: the character_id of the killer (or null for non-murder reveals). */
  val killer_character_id: String? = null,
  /** Reveal-only: identifier for which ending fired (multi-ending support later). */
  val ending_id: String? = null,
  /**
   * Video Clue Pack ($100k stretch goal): URL of a short period-styled video
   * (10-30s) that plays full-screen when this beat fires. Generated by the
   * Speakeater Studio pipeline (Remotion + fal.ai Seedance), stored in R2.
   * Null on text-only beats. Some beats target a single character — only
   * that player sees the video; the rest see a "something is happening
   * across the room" placeholder.
   */
  val video_url: String? = null,
  /** Optional: a poster image for the video (R2-hosted JPEG), used for the loading frame. */
  val video_poster_url: String? = null,
  /**
   * If true and the targeted character has no player joined for the slot,
   * the backend skips this beat entirely (no real beat, no cover beats).
   * Used to gate bonus twists for optional cast members so the table
   * doesn't get phantom cover-buzzes for an absent character.
   */
  val is_optional: Boolean = false,
  /**
   * Director-track stage direction for the host. Renders ONLY on the
   * host's phone, never on a player's. Example: "Read this one quiet,
   * almost to yourself" or "Pause four seconds after the word
   * 'midnight.'" — Blood on the Clocktower / How to Host a Murder style.
   */
  val host_note: String? = null,
)

@Serializable
data class GameBeatAction(
  val id: String,
  val label: String,
)

/**
 * Lobby player payload sent in lobby_update broadcasts.
 */
@Serializable
data class LobbyPlayer(
  val id: String,
  val name: String,
  val character_id: String? = null,
)

// ---------------------------------------------------------------------------
//  Incoming messages — what the server sends to the client. Decoded by
//  GameSocket.parseIncoming and emitted as a sealed class so the UI layer can
//  do exhaustive `when`s.
// ---------------------------------------------------------------------------
sealed class IncomingMessage {
  data class Connected(val role: String, val code: String) : IncomingMessage()
  data class RoomCreated(val code: String) : IncomingMessage()
  data class Joined(val player: LobbyPlayer) : IncomingMessage()
  data class CharacterAssigned(
    val character_id: String,
    val name: String,
    val role: String,
    val secret: String,
    val objective: String = "",
    val bluffs: List<String> = emptyList(),
  ) : IncomingMessage()
  data class LobbyUpdate(
    val code: String,
    val menu_id: String,
    val phase: String,
    val players: List<LobbyPlayer>,
  ) : IncomingMessage()
  data object GameStarted : IncomingMessage()
  data class BeatPushed(val beat_index: Int, val beat: GameBeat) : IncomingMessage()
  data class HostView(val beat_index: Int, val beat: GameBeat) : IncomingMessage()
  data class PlayerActionRecorded(
    val beat_index: Int,
    val action_id: String,
    /** Display name typed by the player at join. Empty string if older
     *  server (pre-2026-05-16) sent the lighter payload. */
    val player_name: String = "",
    /** Cast character_id the player is playing. Lets the host surface
     *  "The Editor chose 'reveal letter'" rather than "Charlotte chose
     *  'reveal letter'." Empty if the player's slot is somehow unassigned. */
    val character_id: String = "",
  ) : IncomingMessage()
  data class Reveal(
    val body: String,
    val killer_character_id: String? = null,
    val ending_id: String? = null,
  ) : IncomingMessage()
  data object Pause : IncomingMessage()
  data object Resume : IncomingMessage()
  data object Pong : IncomingMessage()
  data class ServerError(val message: String) : IncomingMessage()
  data class Unknown(val raw: String) : IncomingMessage()
}

/** Connection state surfaced to the UI. */
enum class GameConnectionState {
  DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
}
