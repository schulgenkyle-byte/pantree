# Mystery Nights as a Multi-Device Interactive Party Game — Plan

Date: 2026-05-12
Status: PROPOSAL — pre-launch decision required before final Kickstarter copy.

---

## The pivot in one sentence

Speakeater's Mystery Nights is not a printable PDF kit. It's a multiplayer party game played across every guest's phone, hosted from one phone, with characters, twists, and actions dispatched in real time. The cocktail and food menu is the production design. The game is the product.

This becomes the headline angle on the Kickstarter campaign.

---

## The play loop (what guests actually do)

**Pre-game (host phone):**
1. Host opens Parties → picks a Mystery Night → taps "Host a Game"
2. App generates a 4-letter room code: e.g. `RYAS`, `TANG`, `LION`, `PEAR`
3. Host displays the code on the phone screen (printable or screenshotable)
4. Guests join as they arrive — the host phone shows the joined list growing
5. Host taps "Begin" when everyone is in

**Joining (each guest's phone):**
1. Guest opens Speakeater → bottom nav "Parties" → "Join a Game"
2. Enters the 4-letter code from the host
3. Phone shows their character card: name, role, secret, what they know
4. They wait for the host to begin

**During play (every phone):**
- The host's phone runs a beat timeline: "T+0", "T+15", "T+30", "T+45", "T+60", "T+90", "Reveal"
- At each beat, the system pushes targeted updates to specific phones:
  - "You overhear the Editor whisper to the Society Matron. Do you (a) approach, (b) note it, (c) ignore?"
  - "The Critic's resignation letter is now public. The Editor will hear about this in 5 minutes."
  - "A waiter delivers an envelope to your table. It's a torn lapel pin. You recognize it. Do you (a) hide it, (b) drop it under the Visiting Author's chair, (c) ask whose it is?"
- Each multiple-choice action affects state. The state pushes new twists to other phones.
- Some twists are host-triggered ("Add a complication"), some are auto-fired by the timeline, some are player-triggered (one player drops a twist that changes another player's character card)
- Host phone shows a live director's-view of who's done what

**The reveal (final beat):**
- Each phone gets the personal version of what their character did and didn't see
- The host's phone shows the full reveal as text + a "share to social" card
- Optional vote: each guest writes down who they thought did it; reveal compares to actual

**The food + drinks layer (unchanged):**
- Menus, recipes, host timeline, shopping list — the existing PartyMenu data
- The host runs the food on its own track. The game runs on top.

---

## Why this is a better Kickstarter pitch than menu-PDFs

| Old framing | New framing |
|---|---|
| "Fifty curated cocktail menus" | "Fifty curated parties, including thirty mystery games" |
| "PDF host kit" | "Multiplayer party game on your phone" |
| Static content | Interactive product |
| Comp set: Paprika, Yummly, recipe apps | Comp set: Jackbox Party Pack, Murder Mystery Co., Hunt-A-Killer |
| WTP: $5/menu | WTP: $10–15/game |
| Single-buyer | Group purchase, group share |
| Solo-host experience | Group entertainment (8 phones at once) |

The Jackbox/Hunt-A-Killer category has proven willingness-to-pay. Speakeater's edge: every game is built on real 1862–1933 cocktail-history primary sources, with the drinks and food as production design that's actually edible. Nobody else is doing both at once.

---

## Architecture

**Stack (extends the existing Cloudflare Workers + D1 + R2):**

```
┌──────────────────────────────────────────────────────────────┐
│  Cloudflare Worker (existing)                                │
│    ├─ /games  routes (new)                                   │
│    └─ Durable Object binding: GameRoom (NEW)                 │
│         └─ One instance per active game room                 │
│            ├─ WebSocket connections (host + N players)       │
│            ├─ Game state (beat, character assignments,       │
│            │   choice history, twist queue)                  │
│            └─ Timer (alarm) for auto-fire beats              │
│                                                              │
│  D1 (existing)                                               │
│    └─ game_session table (record of completed games for      │
│       analytics + the "share your night" recap)              │
│                                                              │
│  Android client (existing)                                   │
│    ├─ feature/parties/game/  (NEW package)                   │
│    │   ├─ HostLobbyScreen.kt                                 │
│    │   ├─ PlayerJoinScreen.kt                                │
│    │   ├─ CharacterCardScreen.kt                             │
│    │   ├─ InGameScreen.kt (the beat-by-beat surface)         │
│    │   ├─ TwistDialog.kt                                     │
│    │   └─ RevealScreen.kt                                    │
│    └─ network/GameSocket.kt                                  │
│        └─ Open WebSocket to wss://pantrie-backend.../game/<code>
└──────────────────────────────────────────────────────────────┘
```

**Why Cloudflare Durable Objects fit perfectly:**
- One DO instance = one game room. State, timer, WebSocket connections all in one place.
- Bounded blast radius — DO instances are isolated. Game RYAS knows nothing about game TANG.
- 100+ concurrent games is exactly the use case. Each game uses ~10 KB of memory. Cloudflare gives us thousands.
- Built-in alarms for the auto-fire beats. No external scheduler needed.
- The DO can hibernate WebSockets when nothing's happening, saving cost.

**Network bounds (Kyle's concern about scope):**
- A code maps 1:1 to a DO instance. Joining requires the code. No code = no entry. No cross-room leaks.
- Codes are 4 letters from a 20-letter alphabet (excludes ambiguous I, O, 0, 1) = 20^4 = 160,000 possible codes. Plenty.
- Codes expire 12 hours after generation. Inactive games garbage-collected.
- Optional code+PIN for stricter privacy (host sets a 4-digit PIN; players need both code+PIN).

**Failure modes + mitigations:**
- Player drops connection: WebSocket reconnect with last-known game state. Auto-rejoin via stored code.
- Host closes app mid-game: game pauses. Host can resume from "Parties → Active Games". Players see "host paused."
- Host phone crashes hard: game state persists in DO for 30 min — host can rejoin.
- Cheating (player tries to peek at another character's card): server-side authorization, each WebSocket message is filtered by character ID. The client only ever receives messages addressed to its character.

**Cost estimate (Cloudflare):**
- DO requests: 100k requests = $0.15
- DO storage: 1 KB per game × 1000 games/month = trivial
- WebSocket: included in worker pricing
- At 1000 games/month: under $20/month server cost. Well within Pro economics.

---

## What ships today (mockups, for Kickstarter screenshots)

For the campaign launching today, the app needs to **show the multiplayer flow without implementing it.** Real backend = post-funding.

**Add 4 new mockup screens to the Mystery Night detail flow:**

1. **HostLobbyScreen** — tap "Host a Game" from a mystery menu detail
   - Big 4-letter code (`RYAS`) in Playfair italic, ~72sp
   - "Players joined: 4 of 5" with a list (Charlotte, James, Marcus, Lila, _waiting_)
   - "Tap Begin when everyone's in" CTA
   - Below: live preview of the game beat timeline

2. **PlayerJoinScreen** — tap "Join a Game" from Parties root
   - Mono code-entry field with one big "JOIN" button
   - Below: small mono note "Ask your host for the four-letter code"

3. **CharacterCardScreen** — what each guest sees after joining
   - The MysteryCharacter content: name, role, secret
   - Plus mockup beat list: "T+15: Look for the lapel pin. T+30: Whisper to the Editor."
   - Period typography (Playfair + JBM)

4. **InGameScreen** — the live play surface
   - Top: current beat (e.g. "T+45 · The reveal of the resignation letter")
   - Middle: a private "incoming twist" card with multiple-choice actions
   - Bottom: "Players still acting" list

5. **RevealScreen** — endgame
   - "It was the Visiting Author." big serif italic
   - Each character's choices summarized
   - "Share this night" card export

**Why mockups for today's screenshots:**
- The campaign promises a working game post-funding
- Screenshots show the design and confirm the vision
- Build estimate becomes credible because the UI exists
- A backer scrolling the Kickstarter sees REAL screens, not concept art

These mockup screens are pure Compose. No backend needed. They render hardcoded "Charlotte" / "T+15" / "RYAS" data. Roughly 600–800 lines of Kotlin total. Doable in 2–3 hours.

---

## What ships post-funding (the real game)

**Build sequence (6–8 weeks solo-dev, fits the $15k–$50k stretch range):**

| Week | Deliverable |
|---|---|
| 1 | Cloudflare Durable Object `GameRoom` skeleton + WebSocket auth + 4-letter code minting + D1 game_session table |
| 2 | Host lobby flow end-to-end (create room, join, start) on real backend |
| 3 | Beat timer + auto-fire beats + per-player message routing |
| 4 | Multiple-choice actions + state mutation + twist dispatching |
| 5 | Reveal phase + share card + game replay viewing |
| 6 | Polish, retry / reconnect logic, observability |
| 7–8 | First 5 mystery games converted from static content to interactive scripts (10 beats × 5 characters × multiple-choice trees) |

**Then on the 10-per-month cadence:**
- 10 more mysteries per month for 5 months → all 30 mysteries shipped by month 13 (June 2027 if KS funds June 2026)
- Stretch funded ($50k+): 25 additional mysteries added to the queue, total 55, ship across 11 months

**The cocktail/food menu side stays in place** — every Mystery Night still ships with the drinks, food, host timeline, shopping list. Those are the production design. The game is the play layer on top.

---

## Stretch goal positioning

Restructure with multiplayer as the headline:

| Threshold | Unlock |
|---:|---|
| $15,000 | Funded. The base 5 Mystery Nights ship interactive at Play Store launch. |
| $25,000 | **Speakeasy World Tour.** Ten international party menus (non-mystery). |
| $40,000 | **Expanded Codex.** All 2,846 cocktails as a free public PDF. |
| $50,000 | **Mystery Nights Expansion.** Twenty-five additional interactive mysteries. Total mystery count goes from 5 to 30. Sent to every backer at $49 or above. |
| $60,000 | **Voice mode.** Mystery beats and cocktail instructions read aloud in a 1920s radio register. Optional. Off by default. |
| $80,000 | **Custom Mystery Builder.** In-app authoring tool — backers can write and share their own multiplayer mysteries with the community. Ships to every backer at $99 or above. |

The headline pitch becomes: "Five interactive mystery games at launch. Thirty-five if we hit $50k. Build your own at $80k."

---

## What the campaign Story should now say

(Draft for the "what this Kickstarter funds" section — replacement)

> **What this Kickstarter funds**
>
> Mystery Nights. Five interactive party games at launch. Built for 4 to 8 phones at a time. Every guest joins via a four-letter code. Every guest gets a character privately on their phone — name, role, secret. The game runs across three hours of dinner and drinks. Plot twists drop into specific phones at specific beats. Actions cascade. The reveal lands at the last cocktail.
>
> Murder at the Algonquin (1924). The Bootlegger's Wife (Chicago, 1929). Last Toast at the Ritz (Paris, 1925). Heir to the Pendennis (Louisville, 1923). The Vanishing Socialite (West Egg, 1926). Each one a real evening with real food and drinks anchored in real bartender's manuals from 1862 to 1923.
>
> Plus fifty party menus on the cooking side — the existing Curate-a-Party set — also shipping at launch.
>
> The campaign funds the build: the Cloudflare backend that hosts up to a hundred concurrent games, the WebSocket layer that pushes twists in real time, the in-app authoring tool that lets me write thirty mysteries this year, and six months of solo-dev runway so all of it ships on June 10, 2026.

---

## Decisions Kyle needs to make

These shape the rest of the build. Pick now or flag for later.

### 1. Game pricing model

- **Option A:** Free-to-host for Pro, $5 per game for non-Pro. Matches current menu pricing.
- **Option B:** $10–15 per game flat (mystery games are richer than menus, can justify higher).
- **Option C:** All multiplayer requires Pro ($45/yr or $30/yr founder). Free trial of the first mystery.

**Recommendation:** Option C. It positions Pro as "the multiplayer key" and makes the founder rate massively valuable. Single-menu Pricing ($5) stays for the non-mystery party menus only.

### 2. Twist trigger model

- **Option A:** Host-only triggers (host phone decides when to drop each twist).
- **Option B:** Auto-fire on a fixed timeline.
- **Option C:** Hybrid — some auto-fire, some host-trigger, some player-vote.

**Recommendation:** Option C. Auto-fire gives reliable pacing for first-time hosts. Host can override for advanced play. Player-vote twists ("Anonymous: should we accuse the Painter?") create emergent drama.

### 3. Player account model

- **Option A:** No accounts. Join by code, device-locked character. Game ends, data gone.
- **Option B:** Optional account for "Mystery Night log" — see games you've played in.
- **Option C:** Required Speakeater account for all players.

**Recommendation:** Option B. Lowest friction (Option A) for the join flow. Optional account collects email for post-game survey + future game offers. Required accounts (Option C) is a friction tax we don't need.

### 4. The "5 ready now" promise

- The current data in `MysteryMenuData.kt` is structured for a host-read solo-device flow.
- The interactive game version needs the same characters and reveal but rewritten as: beat sheet, per-character action trees, twist library, conditional state transitions.
- This is a ~10x scope increase per mystery.

**Recommendation:** The 5 mysteries SHIP at launch as INTERACTIVE versions — that's the campaign promise. They will require 5 weeks of game-design work post-funding (1 week per mystery to convert static → interactive). Built on top of the engine weeks 1–6.

### 5. Today's screenshots scope

- **Option A (recommended):** Ship the 5 mockup screens as part of the Mystery Night detail flow. Hardcoded data. No backend. Screenshots show the design. Campaign launches today.
- **Option B:** Delay launch 2 weeks, build a working alpha. Risky.
- **Option C:** Launch today with the static-only Mystery Night detail. Add multiplayer mockups in a Kickstarter update later.

**Recommendation:** Option A. ~3 hours of Compose work. Real screens. Campaign launches with the right pitch.

---

## What I'd do this session if Kyle approves

1. **Build 5 mockup screens** (HostLobby, PlayerJoin, CharacterCard, InGame, Reveal) — pure Compose, hardcoded data
2. **Add "Host a Game" / "Join a Game" buttons** to MenuDetailScreen for mystery menus
3. **Add a "MULTIPLAYER GAME" badge** to the Mystery Night accordion (replaces or augments the existing "NEW" badge)
4. **Update the campaign copy** with the new framing in `01-CAMPAIGN-PAGE.md` and `02-REWARD-TIERS.md`, regenerate paste files
5. **Update the stretch goal table** to reflect the new structure ($80k Custom Mystery Builder, etc.)
6. **Rebuild + install** on Kyle's phone for screenshot capture
7. **NOT touch the backend** — that's post-funding work

Estimated time: 3 hours. Campaign-launchable today.

---

## Risk surfaces

- **Promise vs deliverable gap.** If we don't ship the multiplayer game by Play Store launch, backers feel misled. Mitigation: explicitly state "Mystery games ship within 90 days of campaign close" in the FAQ.
- **Solo-dev velocity.** 5 mystery games × 1 week each + 6-week engine = 11 weeks. If the campaign closes June 10 and funds late June, that's a mid-September delivery. Communicate this clearly.
- **WebSocket reliability on cellular.** Game runs in someone's basement. Cellular WiFi degraded. Mitigation: aggressive reconnect logic + offline-tolerant state.
- **Group dynamics.** First-time hosts may not know how to drive the game. Mitigation: built-in "first time hosting?" tutorial mode + recorded example walkthroughs.
- **Scope creep.** If we promise multiplayer + 30 mysteries + custom builder, the deliverable list gets dangerous. Mitigation: hard-cap stretch goals at $80k. Don't promise voice mode + multiplayer + builder all at once.

---

## Bottom line

This pivot is the right Kickstarter angle. It moves the product from a content category to a game category, where willingness-to-pay is 3–5x higher and word-of-mouth is much stronger. The technical lift is real but bounded — Cloudflare Durable Objects make 100+ concurrent games trivially cheap.

The decision Kyle needs to make right now: **do we launch today with the mockup screens in place (Option 5A) and tell backers the interactive game ships within 90 days?**

If yes — I build the 5 mockup screens, update the campaign copy, regenerate paste files, rebuild, push to the phone. ~3 hours.

If no — we ship the campaign as-is (Mystery Nights as static role-play kits) and add multiplayer in a campaign update if the response is strong.

Either way: the doc layer is now ready. Pick a path.
