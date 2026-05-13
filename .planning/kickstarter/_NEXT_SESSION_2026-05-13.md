# Next Session Handoff — 2026-05-13

For Claude in the next session. Read this first. Read the linked files in order. Then begin.

---

## Where we are

Speakeater Kickstarter pivoted to **multiplayer mystery party games**. Live in your phone, played across every guest's phone at the dinner table, anchored in pre-Prohibition cocktail history. Comp set: Jackbox + Hunt-A-Killer.

**Backend live:** Cloudflare Durable Object `GameRoom` deployed at `pantrie-backend.schulgenkyle.workers.dev`. WebSocket protocol works end-to-end. Lobby updates broadcast correctly. 100+ concurrent games supported via DO isolation.

**Android scaffolding live:** Host lobby + player join + in-game beat surface + reveal screen. Real WebSocket integration via OkHttp. Build deployed to Kyle's Galaxy S25 as `app.brimm.dev`.

**Five Mystery Nights authored** (static content + interactive scripts):
- Murder at the Algonquin (1924 NYC) — full script
- The Bootlegger's Wife (1929 Chicago) — 3-beat stub
- Last Toast at the Ritz (1925 Paris) — 3-beat stub
- Heir to the Pendennis (1923 Louisville) — 3-beat stub
- The Vanishing Socialite (1926 West Egg) — 3-beat stub

---

## The expectation Kyle set 2026-05-12 — read this carefully

**What I built last session is a working text-reader. It is NOT the game Kyle wants.**

The game Kyle wants is **atmospheric and phone-light**. Players live at the dinner table, not on their screens. The phone is a discreet whisperer of secrets to ONE person at a time. Every phone vibrates at the same instant (cover beats keep observers from pattern-matching who's getting the actionable info). A beat is read in 5-10 seconds. Phone goes back face-down on the table.

The character card up-front is the ONE big read. Backstory, secret, objective, tone notes. Then the game runs by notification.

**Full design doctrine is locked in memory:** `C:/Users/12566/.claude/projects/C--Users-12566/memory/project_mystery_nights_design_doctrine.md` — read this BEFORE writing any code.

---

## Critical files to read at session start (in order)

1. **Design doctrine** — `memory/project_mystery_nights_design_doctrine.md` (what the game is supposed to feel like)
2. **Game-first pivot memory** — `memory/project_speakeater_game_pivot.md` (the campaign positioning)
3. **Backend architecture memory** — `memory/project_mystery_nights_backend.md` (paths, protocol, deployed Version ID)
4. **Pricing memory** — `memory/reference_speakeater_pricing.md` (canonical, no lifetime tier, mysteries SEPARATE from Pro)
5. **Engineering plan** — `.planning/kickstarter/_INTERACTIVE_MYSTERY_GAME_PLAN.md` (the full architecture write-up from when the pivot happened)
6. **Pivot record** — `.planning/kickstarter/_PIVOT_2026-05-12.md` (what changed and why)
7. **Campaign source-of-truth** — `.planning/kickstarter/01-CAMPAIGN-PAGE.md` + `02-REWARD-TIERS.md` (game-first copy, headline-locked)
8. **The 5 launch mystery scripts** — `android/app/src/main/java/app/pantrie/feature/parties/game/GameScripts.kt` (the text-heavy version that needs rewriting per doctrine)
9. **Backend WebSocket protocol** — `backend/src/games.js` (the DO class + handler)

---

## What needs to be built next (priority order)

### 1. Rewrite all 5 mystery scripts to doctrine
Every TWIST beat in `GameScripts.kt` is currently 4-sentence paragraphs. Per doctrine, beats must be ONE sentence max. Action labels must be 1-3 words.
- File: `android/app/src/main/java/app/pantrie/feature/parties/game/GameScripts.kt`
- Murder at the Algonquin has full content; rewrite tight. The other 4 mysteries need full scripts written from scratch (currently 3-beat stubs).

### 2. Add Android push notifications
Phones must vibrate face-down on the table. Currently the app only updates when foregrounded.
- New: `NotificationManager` channel for game beats
- New: foreground service for active game sessions to keep the WebSocket alive while phone is asleep
- Trigger: every `IncomingMessage.BeatPushed` fires a notification with the beat body (or cover text)
- Reference: any standard Android FCM + foreground service guide; the Hilt + Compose stack already in place is compatible

### 3. Cover-beat dispatcher
The most important game-design feature. When the DO fires a targeted beat at character X, EVERY non-target character must receive a cover beat at the SAME INSTANT so observers can't pattern-match.
- Add `cover_beat_library: List<String>` to `GameScript` data class
- In `backend/src/games.js` `_advanceBeat`: when `beat.target !== "all"`, fire the real beat to the target AND a random cover from the library to each non-target
- Cover beats are flavor: "The waiter refills your glass." / "A draft from the hall stirs your napkin." / "The candle on the table burns down a little faster."

### 4. Add `objective` to GameCharacter
Each player needs an agenda for the night to drive their actions beyond reactive twist-clicking.
- Add `objective: String` field to `GameCharacter` in `GameModels.kt` and matching JSON in backend
- Render on the character card prominently
- Example for The Visiting Author (killer): "Avoid being identified before the reveal. Plant the lapel pin on another guest if possible."

### 5. Character card as persistent header
After Begin, the character card should collapse to a small persistent header at the top of the in-game screen, not disappear.
- Update `InGameScreen.kt` in `feature/parties/game/GameScreens.kt`
- Show character name + secret in a 1-line compact header always visible

### 6. Beat lifecycle redesign
- Beat arrives → full-screen takeover with the body (1 sentence) + actions
- Player picks an action OR 8-second timeout
- Screen returns to the persistent character card header
- No "currentBeat persists on screen forever" — beats are ephemeral

---

## Hard rules (from this and prior sessions)

- **No lifetime Pro tier.** Anywhere. Pro at $45/yr retail or $30/yr Kickstarter founder rate forever (grandfathered). Mystery Nights are SEPARATE from Pro (Pro members get one free game as a teaser).
- **Brand voice rules unchanged.** No em-dashes. Period-stop rhythm. Founder-singular "I". No "powerful, seamless, transform, elevate, AI-powered." See `feedback_brimm_writing_voice.md` + `_LIVE_SITE_BRAND_BRIEF.md`.
- **No SVG-as-photography.** Real images via fal.ai Flux Pro Ultra (`Desktop/AI_Auto_vid/generate_menu_heroes.py`). See `reference_fal_ai_image_gen.md`.
- **Don't launch focus-grabbing browsers when Kyle is hands-limited.**
- **Surface every input/output path before any render, deploy, or destructive DB action.**
- **Three names coexist by design** in the codebase — pantrie (codename) / Brimm (Play Store legacy) / Speakeater (user-facing brand). NOT cleanup targets. See `project_naming_canonical.md`.

---

## Deployed state (verify before re-deploying)

- Backend Worker Version ID at last deploy: `96898bb4-8137-42c5-b688-ef67bcc699c5` (2026-05-12, has the re-accept fix)
- D1 catalog: 23,743 food recipes + 6,539 drinks + 2,848 pre-Prohibition cocktails. Slop archived to `*_archived_2026_05` tables.
- Android app on Kyle's Galaxy S25 (`app.brimm.dev`)
- Hero images: 10 in `android/app/src/main/res/drawable-nodpi/menu_*_hero.jpg` (5 party + 5 mystery)
- Tier images for Kickstarter: 4 marquee rendered with corrected "FOUNDER · MMXXVI" copy (post the Brimm → Speakeater + no-lifetime patch). Located at `Desktop/AI_Auto_vid/remotion/out/tier-{49,99,250,1000}-*.png`.

---

## Build + deploy commands

**Cloudflare deploy:**
```
cd "/c/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend"
CLOUDFLARE_API_TOKEN=<token> wrangler deploy
```
(Or `wrangler login` first if you have interactive access.) Token Kyle provided last session is rotated — generate fresh one at dash.cloudflare.com/profile/api-tokens.

**Android build + install:**
```
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:/c/Users/12566/AppData/Local/Android/Sdk/platform-tools:$PATH"
cd "/c/Users/12566/Downloads/pantrie-build (1)/pantrie-build/android"
/c/Users/12566/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle installDebug --no-daemon
```

**Launch on phone:**
```
adb=/c/Users/12566/AppData/Local/Android/Sdk/platform-tools/adb.exe
$adb shell am force-stop app.brimm.dev
$adb shell am start -n app.brimm.dev/app.pantrie.MainActivity
```

**Player simulator for multiplayer testing:**
```
cd "/c/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend"
node test_player.cjs <CODE> <Name>
```

---

## First message Kyle should send the next session

> Read `.planning/kickstarter/_NEXT_SESSION_2026-05-13.md` and follow the next-steps list. Start with rewriting the Murder at the Algonquin script per the doctrine — short beats, cover-beat library, character objectives. Then push notifications. Confirm the read-first list is complete before any code changes.
