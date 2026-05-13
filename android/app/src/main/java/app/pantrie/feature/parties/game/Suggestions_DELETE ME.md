# Gamemaster Audit — Mystery Nights Scripts

Read of `GameScripts.kt` (1342 lines, five fully-authored mysteries). Audit framed around what the host and director need to actually pull off a great room. Friction points first, then concrete fixes per mystery.

## Cross-cutting issues (apply to all 5 games)

These are problems the host hits *in every game*, so fixing them once pays out five times.

### 1. Beat times are out of chronological order in source

The Bootlegger's Wife has `t=48` written before `t=45`. The Ritz has `t=50 → t=42 → t=48`. Pendennis has `t=46` before `t=42`. Socialite has `t=22` after `t=33`, and `t=44/46/48` are jumbled.

Even if the DO sorts by `time_offset_minutes` at dispatch, this is a maintenance bomb. A writer editing the script in source reads them in file order and will make timing errors. Sort all beats by time within `beats = listOf(...)`. Add a unit test that asserts beats are monotonically increasing per script.

### 2. There is no host-facing facilitation layer

Every script is player-beat-only. There's no `host_notes`, no `pacing_advice`, no "if a player has gone quiet, push this card to them," no "if you have 4 players cut these optional roles, if you have 9 add these." The host is supposed to be the conductor and is currently flying blind between beats.

Add a `host_cues` field to `GameScript` with:
- minimum/maximum recommended player count and which characters to drop at each tier
- a 4-line "what success looks like" paragraph for the host so they know whether the table is on track at minute 30, 60, 80
- 2–3 "panic buttons" the host can fire if the table stalls (e.g. "ask the Matron what she's been quiet about")
- a "after the reveal" prompt list so the post-game stays warm instead of dying when the answer drops

### 3. Optional characters hide load-bearing clues

Pattern across all 5 games: the smoothest path to the solution is locked behind a character that the doctrine marks `is_optional`. Couturière in the Ritz literally sees the Heiress poison the glass. Cook in Bootlegger's Wife has the only physical evidence (phone receipt) tying the Partner to Tuesday. Pianist independently corroborates. If a 5-player table runs without them, the killer is uncatchable except by vibes.

Either (a) demote those clues to "hints, not proof" and put the load-bearing evidence on core characters, or (b) when an optional character is dropped, reassign their key clue to a core character via a fallback table. Right now there's no fallback logic in the script struct at all.

### 4. The killer's "deflect" actions are interchangeable

Every killer beat ends with the same shape: confess / accuse X / accuse Y / stay silent. After playing one mystery, players will pattern-match the killer prompt and metagame: "you just got the four-option screen, you're the killer." Hide the tell. The killer's action set should be the same *shape* as every other twist (3 buttons, mostly 1-3 word labels), not visibly longer.

### 5. Murder method is missing from three of five reveals

Pendennis, Algonquin, and Socialite all hand back a *who* and a *why* but no *how*. Players will ask "wait, how did the Youngest Son actually kill the Colonel?" and the host has no answer. The Ritz (digitalis in compact) and Bootlegger (informant tip, not a physical murder) handle this well; the others should add a single sentence of method to the reveal text.

### 6. Frame-up mechanics have no defense

In Algonquin, the Author can "plant the lapel pin under another guest's chair." That's a great mechanic. But the framed guest gets no twist beat telling them they've been framed and giving them a counter-move. Right now, an innocent player can be quietly handed the murder and never know to push back. Whenever a beat lets one player target another, the targeted player needs a paired beat: "Something is under your chair. The light catches it. Decide what to do before someone else sees."

### 7. No physical props are specified

The scripts repeatedly reference written notes, telegrams, photographic plates, lapel pins, and resignation letters as if they exist at the table. They don't. The hosts who turn this into a magical evening will print and pre-hide these. Add a `props_kit` per mystery (downloadable PDF) so hosts can actually produce the telegram the Daughter reads aloud or the plate the Photographer sells.

### 8. Cover-beat library is good but short

18–21 cover lines per game. A 90-minute game with 8 twist beats firing covers to 7 other players = 56 covers, drawn from 18. Players will hear the same line twice, which kills the misdirection. Target 60+ per game, or implement weighted draw-without-replacement at the DO so each player hears each cover at most once.

---

## Mystery 1: Murder at the Algonquin

Strongest of the five for character voice. Witty Round Table cast lands. Author/Editor/Matron triangle is excellent.

**Specific issues:**

- The killer reveal hinges on "twelve years ago in London" — a piece of backstory revealed for the first time at T+65 only to the Author. The Matron's clue (T+38) does mention "twelve years is a long time" but the table has no way to connect that phrase to the Visiting Author until the reveal *tells them* it connects. Players will feel the answer was unfindable.
- The Actress's deduction at T+75 ("the producer's wife was the Visiting Author's sister") arrives fully formed in her phone with zero earlier setup. That's a deus-ex-twist. Plant the sister connection on the Matron or the Photographer earlier so the Actress is *connecting* dots, not *receiving* them.
- The Bellman receives bribery money from the Playwright (T+6) and the Playwright gets a separate note about being seen at midnight (T+10). The Playwright's note essentially is the same fact the Bellman already sold. Collapse or sequence so the Bellman's silence is what the Playwright is buying.
- The "torn lapel pin" appears in the Author's pouch (T+25) AND the Detective's pocket (T+55). Same pin? Two pins? The host will get asked this and have no answer.
- The Playwright's objective ("keep your name out of whatever happens") is the weakest in the cast. Give him a positive goal — recover a specific thing, name a specific person, settle a score with the Critic posthumously.

**Improvement to top-tier:**

Add a "London thread" — three beats across the night, fired to Matron / Photographer / Bellman — each hinting at the Author's prior identity (an accent slip, a name in a passenger manifest, a recognized signet ring). Now when the Author is named, the table can say "we *had* that."

---

## Mystery 2: The Bootlegger's Wife

Tight premise. The "floorboards" slip at T+62 is the best single beat in the entire file — the killer self-incriminates through a knowledge tell. Keep doing exactly that across other games.

**Specific issues:**

- 90 minutes feels 15 too long for the density of twist beats here. Either add 3–4 more mid-game twists or compress to 75 minutes.
- The Wife discovers the railway tickets twice (T+8, T+48). First as "two tickets," second as "the second is in the Singer's name." Works as escalation but the second beat lands awkwardly because the audience never saw the first one resolved. Add a public-facing narrative beat between them so the table knows the tickets exist before the Singer's name lands.
- The Singer's secret ("you let them in") is barely surfaced in play — her only twist (T+18) is "you said nothing." The reveal then says she "watched the agents come and let them in." There's a gap between "did nothing" and "let them in." Pick one and commit.
- The Doorman's envelope is a red herring but the player isn't told that until the reveal. He spends the whole game thinking he might be the killer. That's fine *if* the host knows to enjoy that with him at the post-game — add a host note.
- The Federal Agent is core but his clue (recognizing the Partner from a Tuesday diner meeting) is the only Partner-evidence outside the floorboards slip. If the Agent's player misplays or never raises it, the table is left with one slip and no corroboration. Add a second corroborator on a core character.

**Improvement to top-tier:**

The Singer/Wife/Partner love-and-betrayal triangle is the emotional engine. Lean into it. Add a beat at T+55-ish where the Wife and Singer are forced into a direct exchange, with both characters having a script that puts them on opposite sides of the same question. The most memorable mystery beats are interpersonal collisions, not private notes.

---

## Mystery 3: Last Toast at the Ritz

Cleanest reveal in the file. Frank Meier as the all-seeing bartender who lets the table arrive on its own is a perfect device.

**Specific issues:**

- Frank Meier is too omniscient. He sees the three-minute gap, names the Heiress at T+38, and the reveal says he "knew before the second cocktail." If Frank's player is sharp, the game can end at T+40. Constrain Frank: he knows *something is wrong* but not *who* — make him a great host-proxy rather than a near-solver.
- The Couturière is the only character with eyewitness physical evidence and she's optional. Make her core, or move the compact-into-the-glass observation to Frank (who is already watching closely) and demote the Couturière to a corroborator.
- The Painter's argument at the bar is the *mechanism* that creates the three-minute window. But the Painter's own twist beats never frame his argument as *the* alibi-creator. Make T+9 land harder: "Without realizing it, you may have given the killer the window."
- The Fixer and the Senator both have message-related arcs that go nowhere in the reveal ("unrelated, a real estate matter"). Two characters, same nothing-burger payoff. Cut one or tie one of the messages into the digitalis chain (e.g., the Fixer was hired by the fiancé to delay news of the disinheritance).
- The fiancé is named in three objectives but never appears. Add an off-stage telegram from him at T+45 that the Concierge produces — gives the table a voice that isn't there.

**Improvement to top-tier:**

The Ritz has the best room atmosphere of the five (chandeliers, coupes, brass rail). Build a beats library of *sensory* covers, not just observational ones. Players don't just see the lobby, they smell the tuberose. A few of yours do this already — make it the norm.

---

## Mystery 4: Heir to the Pendennis

Strong Southern-Gothic mood. The Housekeeper as moral fulcrum is excellent.

**Specific issues:**

- The Housekeeper's clue ("saw Youngest enter at 5:45, emerge at 6:00") is a smoking gun. Once she speaks, the game is over. She's also core. So the entire mystery rests on whether her *player* chooses to speak. That's a knife edge — if she speaks at T+46 the game has 44 minutes of nothing left; if she withholds the table flounders. Add internal pressure beats that escalate her urgency over time so the speak/withhold decision is gradient, not binary.
- The murder method is never specified. Did the Youngest poison him? Strangle him? The Doctor's secret hints at a "non-poison that mimics heart attack" but the reveal doesn't say whether that's what was used. Players will ask. Decide and put it in the reveal text.
- The Doctor's secret is ambiguous in a bad way: the Colonel asked the Doctor about an undetectable method. That reads as the *Colonel* contemplating suicide or contemplating killing someone, not the Youngest. The Doctor's clue should point at the Youngest, not muddy the Colonel's intent.
- The Butler's clue (Lawyer in study at 4:30 with a folder) doesn't exonerate the Lawyer and doesn't implicate the Youngest. It sits there as untyped information. Either make the folder matter (it's the proof of the forged checks) or drop the beat.
- The Eldest's "burn the new draft" choice at T+68 has no consequence specified in the reveal. If he burns it, does the old draft stand? Players need to know what their choices *did*.

**Improvement to top-tier:**

This game has the most family-drama potential and is using it the least. Add cross-character beats: the Daughter publicly asking the Lawyer to confirm her telegram, the Eldest publicly demanding the Youngest account for Paris. Right now characters talk to their phones and the room watches. Force them to talk to each other.

---

## Mystery 5: The Vanishing Socialite

Most ambitious concept in the file — there's no murder, the "killer" is the engineerer of a freely-chosen disappearance. Bravo for the swing.

**Specific issues:**

- The `killer_character_id = "the_host"` labeling is misleading. The Host didn't kill anyone. Hosts and players will both read that field and be confused. Rename to `solution_character_id` or add a `solution_type` enum (`MURDER | DISAPPEARANCE | FRAME_UP | OTHER`).
- The win condition is unclear. The Host's objective is "buy three more hours." The game runs 90 minutes. Does the Host win if the table doesn't name him by T+90? Does the table win if they figure out it's a vanishing? Both? Specify a win condition the host can read aloud.
- The Husband's secret (lawn 11:30–midnight, saw figure with Host's pale hair) is a near-smoking-gun planted on a core character at T+13. Same Housekeeper problem as Pendennis: the game might collapse early. Add a misdirect — the Husband saw pale hair but his eyes are bad, or two characters at the party have pale hair.
- Eleven characters (5 core + 6 optional) is the heaviest cast in the file. For a 90-minute game with phones, this is at the limit of what one host can run. Cut to 4 core + 4 optional or recommend a co-director.
- "She left under her own power at twelve oh five through the kitchen garden" — the Driver saw "a young woman matching the socialite's description got into a different car at the back gate at twelve oh five." Different car? Whose? The reveal doesn't close the loop on the getaway car.
- The Sister's "burn the note" option at T+8 destroys evidence the Maid still has access to (mirror's edge). Good asymmetry, but the host needs to be told this is the design.

**Improvement to top-tier:**

This mystery is unique. Lean into it being *unsolvable as a murder*. Add a T+50 beat to all players: "You are no longer sure this is a murder. Listen for what kind of night this actually is." The pivot from murder-investigation to escape-recognition is the emotional payoff and is currently buried in the reveal.

---

## Three things to fix this week if you only have an afternoon

1. Sort all beats chronologically in source and add the monotonic-time test.
2. Write `host_cues` for all five scripts — 4-line success picture, panic buttons, post-game prompts.
3. For each game, audit which clues are locked behind optional characters and either promote them or add a fallback.

---

# Part 2: Is it suspenseful enough? Is the host good enough? Is it intense enough?

You read the scripts and didn't feel it. You're right not to. Four research agents went out and benchmarked Mystery Nights against the masters of the form (novels, party kits, films, live immersive). The honest verdict: the writing is sharp, the bones are good, but the production layer around the writing is mostly absent. Suspense is not built by twist beats. It is built by a clock the table can hear, a host who has been given a directorial script, physical objects passed across the table, forced collisions between characters, and a reveal staged as a moment instead of a paragraph.

The four sections below are the specific receipts.

---

## What the great mystery novels do that this product doesn't yet

The masters do not build suspense by stacking surprises. They build it by making the reader feel a clock they cannot stop. *And Then There Were None* gets its grip from the Soldier rhyme on every bedroom wall and the island's tide chart, not from the murders themselves. Christie tells you exactly how many will die and roughly when, then makes you watch. Mystery Nights has no such counter. The Algonquin script opens with "one of you knows why" and then meanders eighty minutes to a reveal. Add a visible, shared countdown the host invokes out loud, anchored to the world. In Algonquin it is the morning edition going to press at four. In Bootlegger's Wife it is the second federal raid scheduled for midnight. The host should read a beat at T+15, T+30, T+45, T+60, T+75, each one literally a clock advancing ("It is nine forty-five. The presses begin to roll at four. You have six and a quarter hours, and the doors locked behind the last guest at nine"). Christie's *Mousetrap* uses the snowed-in road for the same purpose. Lucy Foley uses the rising tide on Inis an Amplora in *The Guest List*. Pick the physical thing that has trapped these characters in this room, and make the host say it again every fifteen minutes.

P. D. James, in *Talking About Detective Fiction*, says the form's engine is "a closed circle of suspects who know each other and have reason to lie." Mystery Nights already has the circle. What it lacks is the lying. Right now each character has one private secret and one objective. In Sayers's *Strong Poison* and in Tana French's *The Likeness*, the tension is that two suspects know contradictory things about the same event, and the reader watches them both lie about it without quite catching either out. Cross-link the secrets. The Playwright should know the Matron is lying about where she was at midnight, but cannot say so without admitting where he was. The Editor should know the Actress's brother-in-law line is a cover, but needs her protected. Give at least three pairs of characters a hidden mutual hostage in every mystery. That is what makes a dinner table feel like a chess problem instead of a quiz.

Donna Tartt's *The Secret History* opens with the killing on page one and still terrifies. The dread comes from knowing what they did and watching the rest of the cast walk toward it. The killer's player in Mystery Nights currently learns they are the killer in the same envelope as everyone else. Tell them earlier, privately, at T+10, in the second person. "You did it. You are now sitting at a table with five people who did not. One of them already suspects. Survive the next eighty minutes." That single beat changes the killer's body language for the rest of the game, and the rest of the room feels it without knowing why. Patricia Highsmith does the same thing in *Ripley*. The reader is conscripted into the criminal's nervous system.

On clues: Christie's fair-play rule, codified by Knox and Van Dine, is that every fact needed to solve the crime is on the page by the two-thirds mark, and every false fact is contradicted by something else on the page. The Algonquin script gives the Matron a whisper she overheard, the Bellman a bribe, the Photographer a plate, the Maître d' a seating request. Each clue currently lives inside one head. Christie would have the same fact reach two characters at different times in different forms (the lapel pin in the hallway, the torn pin caught in the Author's hem at dinner) so the table can corroborate. Add a "corroboration beat" at T+55 in every mystery where two players' phones buzz with the same fact framed differently. The host says nothing. The two players have to decide whether to compare notes aloud. Anthony Horowitz does this constantly in *Magpie Murders*. The same clue arrives twice in two registers, and the reader (or player) is the one who has to fuse them.

The third-act gut punch lands in Christie, Flynn, and Foley because the reveal recontextualizes a scene the audience already lived through. Gone Girl's diary chapters and Magpie's manuscript-within-the-manuscript both make you re-see what you read. The Algonquin reveal is currently delivered as a single block of host narration at T+90. Break it. The host reads the first paragraph, then the killer's phone buzzes one last time with a private line ("Stand up. Tell them yourself, or let him.") and the killer chooses whether to confess in their own voice or let the host finish. That is the Foley move in *The Guest List*, where the killer interrupts the inquest. Let the room watch one player decide, in real time, whether to take the microphone. That is the gut punch you do not have yet.

---

## What commercial murder mystery party kits do that this product doesn't yet

The honest answer to "is it suspenseful, is the host good enough, is it intense enough" is that the script is sharp but the *production* around it is missing. Commercial kits do not just hand you a story. They hand you a ceremony, a costume, a stack of physical objects, and a host who has been trained (by the kit itself) to perform. Mystery Nights right now is closer to a Twine game than to a Hunt-A-Killer box. The fix is structural, not literary.

The host is the show. Give them a script, not just beats. How to Host a Murder (Decipher, 80s-present) ships a separate Host Booklet with an opening monologue written in character, with a stage direction at the top like *"Speak slowly. A British accent if you can manage one. Lower the lights before you begin."* Between rounds the booklet tells the host things like *"Pause here. Look at the Doctor. Wait until someone fills the silence."* GameScript currently has no host-only content. Add a `host_monologue` field at the script root and a `host_cue` field on every NARRATIVE beat ("read in a conspiratorial whisper, then refill your own glass"). Add a `prompt_quiet_player` flag on key beats that tells the host *"if the table has gone quiet for 30 seconds, call on the Photographer by name."*

Pre-game ritual carries half the suspense. Hunt-A-Killer's onboarding takes 20 minutes before anything starts: opening the wax-sealed box, laying out the case file, putting on the lapel pin. University Games kits include a printed invitation that the host mails a week in advance. The Murder Mystery Company opens with a costumed greeter at the door. Mystery Nights jumps from "tap Begin" to "the critic is dead" with nothing in between. Add a `pre_game_ritual` block to GameScript with a costume note per character, a cocktail or dish suggestion ("the Actress drinks two Aviations in close succession" should be a host-visible setup card, not a buried role line), a printed-or-AirDropped invitation, and a 5-minute opening sequence where the host dims the lights, pours, and reads the monologue while phones stay face-down.

Physical evidence is the discovery engine. Every leading kit ships printed props: telegrams, autopsy reports, fake newspaper clippings, photographs, a torn lapel pin in a velvet pouch. Hunt-A-Killer's whole subscription model is built on this. Mystery Nights has a literal lapel pin in the Algonquin plot that exists only as a sentence on a phone screen. That is the single biggest miss. Add a `props` array to each script with one-page printable PDFs the host downloads and prints (or assembles from household items with a checklist). When the Photographer's plate beat fires, the photographer should be holding a printed photograph. When the resignation letter beat fires, the Editor should slide an actual folded letter across the table. The phone vibration becomes the cue to *produce the prop*, not to read text.

Whisper mechanics need a verb. How to Host a Murder uses "Secret Clue" cards that players slide to each other face-down. Freeform Nordic LARP (see *The Tribunal*, *Just a Little Lovin'*) uses a tap-on-shoulder convention for a sidebar in a designated corner. Right now Mystery Nights has private twists arriving on phones but no sanctioned way to pass private information *between* two players. Add a `PRIVATE_OFFER` beat kind where the app lets one player send a one-line secret to one other player's phone, surfaced as the same vibration pattern as a cover beat so the table can't tell. The Matron's "trade what you saw last night for a favor" objective is begging for this mechanic.

Structure the endgame in two acts, not one. The best kits separate accusation (everyone writes a name, face down) from defense (the accused gets a 60-second monologue to make their case) from reveal. Mystery Nights collapses these into one NARRATIVE then jumps to REVEAL. Add a `DEFENSE` beat kind that fires to whoever received the most accusations and gives them a 60-second timer to talk. Add a `MOTIVE_VOTE` beat where the table votes not just on who, but on why.

The warm-down is where memory is made. Murder Mystery Company hosts run a 10-minute debrief: each player reads their secret aloud, the host names the MVP, group photo. Add an `EPILOGUE` beat sequence after REVEAL: each character gets one final private beat ("read aloud: what your character did the morning after"), then a host-prompted toast, then a `group_photo_prompt`. Without this the night ends on the host reading a paragraph and everyone reaching for their phones.

One last authoring trope worth stealing. How to Host a Murder gives every character a per-round must-do ("In Round 2, you must ask the Doctor about the will before the round ends"). The current `objective` field is the whole-game version. Add `round_task` per character per act so even quiet players have a concrete next move on their screen at all times. That is the single highest-leverage change for intensity, because intensity in a dinner game is just every player having something they urgently need to do right now.

---

## What the great mystery films do that this product doesn't yet

The current scripts have great bones (one-sentence twists, real motive depth, the floorboards-style killer slip, a closing reveal that names every thread). What they're missing is the directorial layer. The films below earn their suspense from things happening *between* the beats, not just inside them. Here is the gap, scene by scene, with concrete additions.

In Knives Out the entire first act is interrogation, and Rian Johnson stages every answer as a private flashback the audience watches while the detectives hear only the dialogue. The audience knows more than the room. Right now your scripts let every player know only what their own phone tells them and what the host reads aloud, which flattens the dramatic irony that drives Knives Out and Glass Onion. Add a `PRIVATE_FLASHBACK` beat kind. One player gets a 60-second longform paragraph on their phone and is told to read it aloud as a recollection while the room listens. The other phones buzz cover during the read so observers cannot tell whether the speaker is recalling, lying, or confessing.

Clue 1985 and The Last of Sheila both lean on physical handoffs. A key. A pin. A telegram. The current Algonquin script has the lapel pin and that is the strongest object in the whole game. Lean into this. Every mystery should ship with three physical props the host hands out at specific beats (a sealed envelope, a folded napkin with a name, a calling card). Phones are private. Props are public. Public objects passed across the table are how Clue gets six suspects to feel like they are in the same room.

The Mousetrap and Death on the Nile both compress the third act around a ticking external clock the room can hear. In Mousetrap it is the radio bulletin. In Nile it is the boat reaching Wadi Halfa at dawn. Your Algonquin manager has a "morning edition prints at four" line and your Vanishing Socialite has a one-ten clock chime, but these are buried in private twists. Promote the clock to broadcast. Add a `CLOCK` beat that every phone vibrates in unison at T+60, T+70, T+80, with the host reading "twenty minutes until the morning edition" aloud. The synchronized buzz is the same trick Knock at the Cabin uses every time the family is told another choice is coming.

Gosford Park's masterpiece is the dining scene where Maggie Smith's casual cruelty forces two characters who would never speak to look at each other. The scripts have the secrets in place for these collisions but never command them. Add a `FORCED_PAIR` beat kind that fires to exactly two phones at the same instant. Both buzz. Both read "the room has gone quiet. You and the Singer are the only two not talking. Speak first." This is the kitchen confrontation in Knives Out. It is engineered by the script, not hoped for.

Columbo's whole structural innovation is the inverted mystery. The audience knows the killer in the first ten minutes. The tension is the cat-and-mouse. Your scripts could borrow this for one of the five (Bootlegger is the natural candidate) by adding an optional mode where one non-killer player privately knows who did it from beat one and has to maneuver them into the slip. This makes the table-level tension visible because someone at the table is hunting on purpose.

The drawing-room reveals in Poirot and Benoit Blanc work because of rhythm, not content. Poirot names the red herrings first, dismisses them one by one with a sentence each, then names the killer, then explains the motive, then explains the method, in that order. Your current REVEAL beats name the killer in the first sentence and then explain. Restructure: clear the herrings first ("It was not the Editor. The resignation letter was real. It was not the Playwright. He was paying the bellman for an alibi he did not need."), then the pause, then the name. The host should be instructed to stop and look around the table before the name lands. The Mousetrap script literally directs Trotter to pause for a count of three. Put that stage direction in the host's read.

The Menu and Saltburn build dread through atmosphere rather than information. Candles dim. The music changes. The room temperature drops. Your phones can borrow this. Add a `ROOM` beat that does nothing but tell the host "lower the lights one notch" or "play the slow piano cue" or "have the bartender set down a fresh bottle without a word." Atmosphere is free. The phone is the cue sheet.

And Then There Were None earns its dread by killing characters between beats so the survivors get fewer and fewer. You cannot kill players, but you can sideline them. Add an `EXIT` beat that pulls one character out of the action for ten minutes (they step into another room, they pass out, they are sent for the manager). The remaining table gets denser. The pressure on the killer rises.

Two host-craft notes from Benoit Blanc and Inspector Trotter. Both interrupt. Both repeat the last thing a suspect said back to them in a different tone. Give the host a `HOST_PROMPT` beat that fires only to the host's phone every five minutes with the line "ask the quietest person at the table what they were doing at eleven-forty," or "repeat the last sentence the Author said, slower." This is the missing director track.

---

## What live immersive and social-deduction games do that this product doesn't yet

The script reads like a well-plotted radio drama with a host who is treated as a metronome (read line, dispatch beat, wait). Live experiences treat the host as the show. The atmospheric chassis around the table is also missing, which is what Sleep No More, Hunt-A-Killer, and a good escape room sell first and puzzles second.

Atmosphere arrives before the first beat. Punchdrunk's rule at the McKittrick is that the audience puts on the mask before they enter, and they cannot speak once inside. That single constraint creates the whole show. This game has no equivalent threshold ritual. Add a five-minute "doors open" preamble in the script: a candle-lighting line for the host, a "phones face-down until they buzz" rule, a single Berlin or Whiteman track the host is told to cue on a speaker, and a costume floor (one period item, a hat or a pin). Hunt-A-Killer ships a wax-sealed envelope for the same reason. The phone is the puzzle, but the phone should be the second thing players notice, not the first.

The host needs a Storyteller manual, not a beat list. Blood on the Clocktower's Storyteller has a 40-page book of rulings, tone guidance, and pacing notes. They are told when to look bored, when to lean in, when to call a player into a private corner for thirty seconds of nothing just to make the rest of the table nervous. The current GameScript has narrative lines but no stage directions. Add a `host_note` field on every beat: "Read this one quiet, almost to yourself" or "Pause four seconds after the word 'midnight.'" Add a small HOST-only deck of "stall actions" the host can fire when the table is going too fast: ask the Matron to refill the Author's glass, ask anyone wearing pearls to stand and recite their character's drink order. Botc-style.

Cover beats solve pattern-matching but not suspicion theater. Werewolf's "everyone close your eyes" is theater. The wolves do not need closed eyes mechanically. The closing-of-eyes is what makes the reveal land. This game's synchronized buzz is the mechanical solve; it needs the theatrical equivalent. Add a periodic SUMMONS beat: every 12-15 minutes the host calls one named player to "step into the hallway" with the host for 30 seconds, sometimes for a real private exchange, sometimes for nothing. Avalon's quest-team huddle. Secret Hitler's chancellor-pick. Thirty seconds of two people whispering with the table watching is the highest-tension unit in social deduction and the game does not use it.

Killers need bluffs. Botc gives evil players a pre-written list of good-role abilities they can claim. It is the single biggest reason new players survive the first night. Add a `bluffs: List<String>` field to every character marked as the killer, with three pre-written cover stories they can deploy verbatim ("I was in the kitchen with the maitre at twelve-fifteen, ask him"). The killer player is doing the hardest improv at the table and the script gives them nothing.

There is no escalation curve, only a beat schedule. A great 60-minute escape room hits a "we are stuck" trough at minute 35 and a breakthrough at 45. The Algonquin script fires twists evenly. Mark beats with an intended energy level (HUSH, BUILD, SPIKE, BREAK) and write a 5-minute SILENCE window at minute 55 where no twists fire and the host is instructed to let the table argue. The dead air is the suspense. Jackbox does the opposite, which is why Jackbox is exhausting after 90 minutes and a mystery is not.

The reveal is a moment, not a line. Werewolf hosts know to draw "the wolves were..." out across ten seconds of silence. Hunt-A-Killer's final envelope is a physical object passed around the table. The current reveal beat is a single NARRATIVE line. Replace it with a three-step ritual: the host asks every player in turn for one sentence on who they think did it, then a synchronized buzz fires the verdict to every phone at once, then the host reads a final paragraph. Three beats, not one. The accused player should also get a private LAST WORD beat 30 seconds before the reveal: one sentence to defend themselves, fired only to them.

Disengagement has named tools. Megagame Control teams hand "interrupt cards" to bored players: a new objective on a slip of paper. Add an optional `bored_player_beat` library the host can manually fire from a hidden panel: a fresh objective, a forced toast, a "go fetch the bellman a drink" errand. Solves the introvert, the early-solver, and the drunk uncle in one mechanism.

There is no after. Sleep No More has the Manderley Bar where the audience compares notes for two hours after the show. Escape rooms do the team photo and the "you escaped in 47:32" board. Add a POST beat at T+92: a host script that walks the table through "what each of you was actually trying to do tonight," then a phone-buzz to everyone with their character's full secret history (the parts that never came up), then a prompt to take one photograph with a suggested caption. The night is remembered by the last ten minutes, not the middle.

---

# What changes if you take this seriously

The five highest-leverage additions, distilled from all four agents:

1. A shared, world-anchored ticking clock the host reads aloud at T+15/30/45/60/75. (Christie, Mousetrap, Death on the Nile, Knock at the Cabin.)
2. A host script with stage directions on every beat, plus a panic-button "stall actions" deck. (How to Host a Murder, Blood on the Clocktower Storyteller manual.)
3. Physical props printed and pre-placed: telegrams, photos, the actual lapel pin, the resignation letter. (Hunt-A-Killer, Clue, every commercial kit shipping today.)
4. New beat kinds: `FORCED_PAIR` (two phones buzz, those two players must speak first), `PRIVATE_FLASHBACK` (one player reads a 60-second recollection aloud), `SUMMONS` (host pulls one player into the hallway for 30 seconds, sometimes for nothing), `CLOCK` (synchronized buzz to all phones with a time-pressure line), `DEFENSE` (60-second monologue from the most-accused), `EPILOGUE` (debrief beats per character after REVEAL).
5. Killer-only `bluffs` array with three pre-written cover stories, and a killer-first private notification at T+10 in the second person ("You did it. Survive the next eighty minutes.").

If you add nothing else, the clock and the host script alone will double the felt intensity.

---

# Part 3: The north star — world-class and immersive, phones DOWN

This is the design constraint that should govern every other suggestion in this document. Your stated goal: a world-class immersive evening where the players are *not on their phones all night*. The phone is a cue device, not the experience. Reread every suggestion above through this lens, and some of them rank differently. Here is the audit, against the north star.

## Phones-down is a feature, not a tradeoff

The best immersive experiences in the world (Sleep No More, The McKittrick, Then She Fell, the better escape rooms) confiscate or constrain the phone *on entry*. Hunt-A-Killer is a paper experience. Clue is a board. How to Host a Murder is a booklet. Werewolf does not exist on a screen. The reason these experiences work is that the players' attention is on each other's faces. The moment a player looks down at a phone for more than three seconds, the spell breaks for everyone else at the table. Your synchronized cover-beat mechanic already understands this in the technical sense (no one knows whose beat is real). It does not yet understand it in the *attention* sense (every beat still costs everyone three seconds of downward gaze).

## The phone should buzz, not be read

Right now, every beat sends text to a phone and expects the player to read it. That means at any moment, 1 to 11 players are looking down. World-class is the opposite. Adopt this rule across the codebase: the phone vibrates as a *summons*, and the player then performs an off-phone action. The text on the screen should be the shortest possible imperative ("Pour him a drink. Say nothing.") and the *interesting* content lives in the action the player takes at the table.

Today: phone says "A bellman slips you a note: Seen returning at midnight. The lobby man forgets, for a price. [Pay him] [Ignore] [Read aloud]." Player reads 30 words on a phone, taps a button, looks back up.

World-class: phone vibrates. Player looks down. Phone says "Ask the Bellman, out loud, what he wants for what he saw at midnight." Three words of stage direction. Player looks up. The conversation happens at the table. The drama lives in faces.

That single rewrite would cut the average per-beat screen time from twenty seconds to three. It would also turn every twist into a *public* moment instead of a private one, which is exactly the intensity gap you noticed when you read the scripts.

## Props are the real interface

Several agents flagged props. Under the phones-down north star, props are not a nice-to-have, they are *the primary medium*. The phone tells the player which prop to use. Reframe every existing twist beat to land on an object. The resignation letter is a folded envelope in the Editor's pocket from the start of the evening. The lapel pin is a real pin. The photographic plate is an actual printed photograph in a paper sleeve. When the Editor's phone vibrates at T+12, the screen does not say "the resignation letter is in your inside pocket." It says "Now." The player already knows what's in the pocket because they put it there during setup.

This also fixes the prior "no defense for framed players" problem. The lapel pin is a real object. If the Author plants it under another player's chair, that player will find it physically and can produce it. There is no need for a separate counter-beat.

## The host carries the room, not the app

The other consequence of phones-down is that the host's voice becomes the connective tissue. Every NARRATIVE beat should be read aloud with the room watching, not pushed to screens. The host's phone is a cue card the host reads from, not a script the room reads silently. Move all NARRATIVE beat text to the host's phone only. The other players get a haptic buzz that signals "stop talking, the host is about to speak." That is the Punchdrunk threshold rule, adapted: phones face down, then a buzz, then the host speaks. Players' eyes go to the host, not to the screen.

## Reread the prior suggestions through this filter

Several suggestions above are *more* aligned with phones-down than they look. Promote them:

- The `CLOCK` beat (synchronized buzz, host reads time aloud) is perfect under this rule. Adopt without changes.
- The `FORCED_PAIR` beat (two phones buzz, those two must speak first) is exactly the design pattern. Adopt without changes.
- The `SUMMONS` beat (host pulls one player into the hallway for 30 seconds) is the McKittrick move. Adopt without changes.
- The `host_note` and `host_cue` stage directions go on the host's phone only. Adopt without changes.
- The `EPILOGUE` debrief is verbal at the table. Adopt without changes.

Several suggestions need to be modified to fit the north star:

- The `PRIVATE_FLASHBACK` (one player reads a 60-second paragraph aloud) is fine, but cap the on-screen text at 60 words and have the player *paraphrase* rather than read. Otherwise it becomes a reading test and the room loses energy.
- The `PRIVATE_OFFER` (one player sends a one-line secret to another) is better done as a physical note slid across the table. Use the phone only to tell the sender "write a one-line secret on the napkin in front of you and slide it to the Singer."
- The "killer learns at T+10 in second person" beat is great, but the text should be one sentence ("You did it. Survive eighty more minutes."), not a paragraph. The killer should not need to look down again.

Reject one prior suggestion outright:

- The `DEFENSE` beat that fires a 60-second timer to the accused's phone is wrong under this rule. There should be no timer on a screen. The host names the accused, says "you have one minute, speak," and the host's phone shows the timer. The accused never looks down.

## Cap the screen budget per character per game

Set a hard design rule: each player's phone is used no more than 12 times over 90 minutes, for no more than 10 seconds per use. That is a total of 2 minutes of screen time across a 90-minute game, or roughly 2.2% of the evening. Every other minute is at the table. Add a script-validation test that counts the targeted beats per character and fails the build if any character exceeds 12. This is the kind of constraint that forces the writing to get sharper.

## The atmospheric chassis you do not yet ship

World-class immersive sells the *room* before it sells the game. To get to top-tier, ship a "Mystery Nights Evening Kit" per mystery that includes:

- A printed host booklet with the opening monologue and stage directions, so the host can run from paper instead of a phone.
- A printed character dossier per player, sealed in a wax-stamped envelope, opened together at minute zero. Each dossier has the role, secret, objective, and three cover-story bluffs the player can claim during the evening.
- The physical props: lapel pin, resignation letter, photographic plate, telegram, calling cards for the accusation phase. One sealed manila envelope per prop, labeled with the beat number, opened by the host at the right moment.
- A music cue sheet on the host's phone with one-tap track changes (a Whiteman number for arrivals, a slow piano cue for T+60, a sudden silence at T+88 before the reveal).
- A cocktail list per mystery, three drinks max, period-correct.
- A costume floor (one item per player, period). The phone reminds the host to mention this in the invitation, but does not enforce it.

Most of this is paper and PDFs, not software. That is the point. The app's job is to coordinate the evening, not be the evening.

## The one-line definition of done

A guest, the morning after a Mystery Nights, should describe it to a friend as "the night Sarah finally cracked at the table after the third toast," not as "we got these texts on our phones and tapped buttons." If the story they tell is about each other, you've won. If the story they tell is about the app, you've lost. Every suggestion in this document, accepted or rejected, should be measured against that single test.

---

# Part 4: Physical movement, sneaking, and covert intel — the missing layer

Honest answer to the question: no, the current scripts do not force players to step away, sneak out, gather intel covertly, or be missed by the table. The button label "Step out" appears exactly twice in the entire 1342-line file (line 498 for the Partner in Bootlegger, line 980 for the Youngest Son in Pendennis), and both are decorative. Tapping the button does not make the player physically leave. Every action resolves at the table. Every clue arrives by phone vibration. Every secret is shared (or not) through conversation.

This is the single biggest immersion gap in the product, and it is the easiest one to fix because the world-building already implies the spaces: the Algonquin lobby, the Ritz front desk, the Pendennis study, the West Egg powder room, the bootlegger's back kitchen. The scripts reference these rooms constantly. They just never send anyone into them.

## The design opportunity

Suspicion in a real dinner party comes from absence. A guest excuses themselves to the bathroom and stays gone for six minutes. A guest steps out to take a call. Two guests are missing at the same time. A guest comes back flushed and changes the subject. This is the texture of every great party-as-mystery in fiction (Gosford Park's hallway shots, the dining-room exits in Knives Out, every scene in Saltburn where someone goes upstairs alone). It is also the engine of Werewolf, Mafia, and Blood on the Clocktower, where the wolves "wake up" and the rest of the table is left to wonder who was active.

The current scripts simulate this with synchronized buzzes (everyone's phone vibrates so observers can't tell whose beat is real). That works for *private information* but not for *physical absence*. You cannot fake a player not being in their chair.

## The privacy doctrine, made explicit

Before the new beat kinds, lock the privacy rule in writing as a host-script preamble that the host reads aloud at minute zero:

> Your dossier is yours. Your phone is yours. Anything that buzzes on it is yours to use, share, or keep. You may lie. You may bluff. You may invent. The only rule is that you do not show another guest your screen, you do not read another guest's dossier, and you only volunteer a secret when *your phone tells you to or you choose to*. The night is yours to play.

Add this as the `privacy_preamble` field on `GameScript` and have the host read it before any other beat fires. This is the rule the rest of the design depends on.

## New beat kinds to add

The following beat kinds turn the off-stage spaces into a real game layer. Each one creates physical absence at the table, which creates suspicion, which creates the kind of evening you actually want.

`STEP_AWAY` — phone vibrates, screen says "Excuse yourself from the table. Go to [the powder room / the bar / the front desk] and stay gone for [3 / 5 / 7] minutes." A timer runs on the phone. The player must physically leave. When the timer ends, the phone vibrates again and the player returns. The table sees the chair empty. This is the basic unit of suspicion.

`RECON` — a `STEP_AWAY` with a payload. Phone vibrates, says "Step away to the lobby. While you're gone, listen for the bellman saying the words 'twelve-fifteen.' Return when you hear it or after 5 minutes." Now the absence has a purpose, but only the player who left knows what they were looking for. They return with information (real or imagined) that no other player can verify.

`GATHER_INTEL` — phone vibrates, says "The Photographer's plate is in a coat pocket on the rack by the door. While the table is debating the toast, retrieve it without being asked what you are doing." The player must physically execute the task. If they get caught, they get caught. Other players' phones do not buzz. This is the heist mechanic.

`RENDEZVOUS` — two phones vibrate at the same instant with the same instruction: "Meet in the hallway by the kitchen, alone, for two minutes. Decide what you tell each other. Return separately." Two empty chairs at once. Maximum suspicion. The Matron and the Author meeting in the hallway is a thousand times more dramatic than the Matron texting the Author her secret across the table.

`SNEAK` — phone vibrates, says "Without standing up, slip the lapel pin into the pocket of the guest to your left." Or "While the host is reading the next narrative beat, switch the place cards between the Singer and the Wife." A physical action performed in plain sight that must not be noticed. The Maître d' in *The Last of Sheila* does this constantly. So does Daniel Craig's Blanc.

`DEAD_DROP` — phone vibrates, says "There is a folded napkin under the centerpiece. Take it. Read it. Replace it." A physical object that lives in a fixed location in the room, that multiple players will be told to visit at different times, each finding a different message (the host swaps cards between visits using their own buzz cue).

`BE_SEEN` — phone vibrates, says "Stand up. Walk slowly past the Author's chair. Let her see your face. Sit back down." This is the cinematic eye-contact moment. The Author's phone buzzes at the same instant: "Look up. The Husband is walking past. Hold his eye until he sits."

`MISSED` — fires to all phones except one. Says "The Photographer has been gone for four minutes. Notice this aloud." Forces the table to register an absence that they otherwise might miss. This is how Werewolf hosts make the night phase land.

## Privacy is enforced by the absence of broadcast

The crucial design rule is that none of the above beat kinds broadcast their *content* to anyone except the targeted player or pair. The other guests see an empty chair. They do not see what the phone said. They can ask the returning player "where were you" and the player can lie. The host knows because the host's phone shows the full schedule. The host says nothing.

This is the structural difference between Mystery Nights as it stands today (every clue is private text on a phone) and the world-class version (every clue is *evidence of a physical event* that the table watched happen). When the Editor returns to the table after a 5-minute absence with the resignation letter visibly in hand, every other guest knows something happened. They do not know what.

## Per-mystery insertions

For each of the five, here are the highest-leverage off-table moments to add. None of these require new fiction. The spaces already exist in the world-building.

Algonquin. The Playwright `STEP_AWAY` at T+12 to the lobby to pay the Bellman. He returns four minutes later with a real folded bill in his hand that he never explains. The Author `RECON` at T+25 to the cloakroom to retrieve her own torn lapel pin from her coat (the prop is actually there). The Matron and the Editor `RENDEZVOUS` at T+50 in the dining-room doorway for two minutes to discuss the actress without the actress hearing. The Detective `DEAD_DROP` at T+55 to place the pin under the Author's chair while crossing to the bar. The Photographer `BE_SEEN` at T+30 carrying a printed plate past the Author's seat, slowly.

Bootlegger. The Partner `STEP_AWAY` at T+24 to the back kitchen to take a phone call. Returns six minutes later. The Cook `MISSED` fires to the rest of the table while he is gone. The Singer `RECON` at T+18 to the alley to see who the agents are with, returns five minutes later with a name nobody can verify. The Wife `SNEAK` at T+8 to slip the two railway tickets out of her husband's coat (the coat is on a chair across the room). The Federal Agent `BE_SEEN` at T+62 to walk slowly past the Partner's chair after the floorboards slip, then return without speaking.

Ritz. The Heiress `STEP_AWAY` at T+13 for three minutes to the dining room, recreating the same window in which she poisoned the glass. Frank Meier `BE_SEEN` watching her go. The Couturière `GATHER_INTEL` at T+30 to retrieve the Heiress's compact from the powder room (a real compact, with a real powder, is there). The Concierge and the Fixer `RENDEZVOUS` at T+50 at the front desk for two minutes. The Cigarette Girl `DEAD_DROP` at T+42 to leave the folded note on the bar where any guest can find it.

Pendennis. The Youngest Son `STEP_AWAY` at T+20 to the study for five minutes, recreating the original 5:45-to-6:00 window. The Housekeeper `BE_SEEN` watching him from the doorway as he goes. The Daughter `GATHER_INTEL` at T+13 to retrieve the telegram from her own room down the hall. The Butler and the Family Lawyer `RENDEZVOUS` at T+42 in the library for two minutes to discuss the folder. The Eldest Son `SNEAK` at T+8 to lift the new draft from the lawyer's coat across the room.

Vanishing Socialite. The Sister `STEP_AWAY` at T+8 to the powder room to retrieve and burn the note (a real note in a real mirror frame). The Husband `STEP_AWAY` at T+13 to the lawn for four minutes, recreating his own alibi window. The Bandleader `BE_SEEN` walking past the library doors twice. The Driver `GATHER_INTEL` at T+50 to the back gate to confirm whether the second car is still there. The Aviator and the Host `RENDEZVOUS` at T+68 in the kitchen for two minutes. The Photographer `DEAD_DROP` at T+48 to leave the plate face-up on the side table.

## What this changes about the felt experience

Right now the scripts produce an evening where eleven people sit around a table tapping phones and occasionally speaking. With the additions above, the same evening produces a house in motion. A chair empty at minute 13. A guest returning from the lobby with a folded bill. Two guests missing at the same time for two minutes. A pin appearing under someone else's seat. A glance held too long across the room. Every one of these is suspicion, generated by the script, controlled by the host, and totally invisible on any phone screen except as a three-word instruction nobody else sees.

That is the world-class immersive experience. The phone is the conductor's baton. The orchestra plays in the room.

## Implementation note

These beat kinds need the DO to support per-character timers (so the player who stepped away gets a return-buzz five minutes later), per-room state (so the host knows which rooms are "in use"), and a conflict-avoidance check (so two `STEP_AWAY` beats to the same room do not collide unless that's the intent). None of this is hard. It is one schema migration on `GameBeat` and a `room_state` map on the DO. The script work is the hard part and you have already done it.

Everything else above is polish on top of a strong base. The character voices, period detail, and twist-beat brevity are already at a level most commercial murder-mystery kits don't reach. Tighten the host's seat and the rest comes.
