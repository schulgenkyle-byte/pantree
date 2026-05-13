# Prelaunch Funnel — 14 days to launch

The campaign launches cold and dies fast if the email list isn't built before day 0. This is the prelaunch playbook.

---

## Email capture target

500 email signups before campaign opens.

Why 500: at a 15% campaign-day-1 conversion rate, that's 75 day-1 backers. At an average $35 pledge, that's $2,625 on launch day. Kickstarter's algorithm favors projects that get to 30% of goal in the first 72 hours. $4,500 in 72 hours hits that mark.

If you can get to 1,000 emails, double everything above. The math is linear up to about 5,000 prelaunch emails (after that the conversion rate degrades).

---

## Where the capture lives

**Primary:** speakeater.com/kickstarter (new page on the existing speakeater-site Cloudflare Pages deploy)

The page needs four things:
1. The headline ("Open your fridge. We'll figure out dinner. And the cocktail to pair with it.")
2. A two-paragraph teaser (lifted from the campaign-page intro)
3. The email capture form (one field, one button)
4. A "what you'll get for signing up early" hook (see below)

**Secondary:** In-app "Coming soon to Kickstarter" banner on the Mixology tab. Only shown to non-Pro users. Tapping it deep-links to the email-capture form on speakeater.com/kickstarter via an in-app web view.

**Tertiary:** A short Linktree-style page in the Instagram bio (link.speakeater.com), driving social-channel traffic to the same form.

---

## The early-signup hook

Pre-launch emails need an exchange. Free PDF of one sample menu (The Bee's Knees Garden Party — already written) sent to anyone who joins the list. The PDF is also a soft-validation: anyone who opens the PDF email is twice as likely to back. We track open rates.

The PDF is sepia-themed (Bootlegger aesthetic), six pages, watermarked "Speakeater Backer Preview — not for redistribution" in the footer. Generated once, stored in R2, served from a signed URL via a Cloudflare Worker.

---

## Email sequence (5 emails over 14 days)

**Email 1 (immediate, on signup):** Welcome + PDF link

Subject: Your menu is here. Plus what's coming next.

Body opens with a quick note from Kyle. Two paragraphs. The PDF download link. A line at the bottom: "I'll send four more emails over the next two weeks while we get ready to launch. If that's too much, the unsubscribe link is below. No hard feelings."

**Email 2 (day 4):** The story behind the app

Subject: Fourteen months in my kitchen

Body: the founder narrative. Where Speakeater came from. The cocktail history obsession. The waste problem in my own fridge. No pitch. Just the story. Single CTA at the bottom: "Reply to this email if you want to ask me anything."

The replies build relationships before launch day. Every reply is a backer-grade signal.

**Email 3 (day 8):** What you'll see on Kickstarter

Subject: The reward tiers, one week early

Body: the full tier ladder ($1 to $1,000) laid out in plain language. No prices teased above the inbox preview. Make the email feel like a behind-the-scenes briefing, not a sales pitch. End with: "Day 0 is [DATE]. I'll send the launch link the morning of."

**Email 4 (day 12):** Two days out

Subject: Two days until launch

Body: short. The mechanics. When the campaign goes live. What to do (tier tip: the $25 Beta Backer tier is the inflection point that unlocks Internal Testing access). One sentence reminding them their email got them onto the list early; the public won't see the campaign until Tuesday.

**Email 5 (day 14, launch morning):** The link

Subject: Speakeater is live on Kickstarter

Body: the campaign URL, the funding goal, the 30-day window. One paragraph reminder of the stretch goals. A simple "tap here to back" CTA.

---

## Social media cadence (4 weeks pre + 30 days campaign)

Per memory rule: every public post must lead with what shipped/changed, journal voice TO followers, not pitch voice AT prospects. Apply that here.

**Frequency:** 4-6 posts per week. Mix of formats.

**Channels (priority order):**
1. **Instagram** (primary). The aesthetic is the channel. Curate-a-Party menus photograph well. Cocktail history posts work natively.
2. **TikTok / Reels** (cross-post the same video). Vertical reformulations of the hero video, 60-second cocktail demos, "I made the X drink from 1925" format.
3. **Twitter/X** (founder voice). Build-in-public style: yesterday I fixed billing, today I'm writing menus, tomorrow I'm shooting the trailer. The honest grind.
4. **Reddit** (carefully). r/cocktails, r/whiskey, r/Prohibition (yes, it exists), r/IndieDev. Be a contributor first, advertiser second. One post per subreddit max during prelaunch.

**Post types:**
- The Story Post: "Why I'm building this. Here's the cocktail that started it all."
- The Behind-the-Build: "Today I [fixed billing / wrote a menu / shot the trailer]"
- The Cocktail History Card: One drink, one bar, one bartender, one year. Photo of the drink. Two-paragraph history.
- The Menu Preview: One Curate-a-Party menu's hero image + the elevator description.
- The Counter Post (post-launch only): "We crossed [milestone]. Thank you. Here's what unlocks at the next stretch."

---

## Influencer outreach

Target: 30 outreach attempts in the 14 days before launch. Expected hit rate: 10-15%. Realistic gets: 3-5 collaborators.

**Tier 1 (50k-500k followers, cocktail/history specific):**
- @greatestbarintheworld (cocktail history, 580k followers)
- @anistarviet (cocktail content, history-leaning)
- @lifebehindbars (bartender community)
- @drinkswithguardian or similar editorial cocktail accounts

**Tier 2 (5k-50k followers, niche but engaged):**
- Bartending guild local chapter accounts
- Prohibition-era history hobbyists (look up Prohibition Museum St. Louis follower lists)
- Vintage cocktail YouTube channels under 50k subs

**Tier 3 (the friends-of-friends play):**
- Anyone Kyle knows personally who has more than 1,000 followers in the food/drink/design space. The personal ask beats the cold pitch every time.

**Pitch template:** plain-text email or DM. Three sentences. (1) What Speakeater is. (2) Why their audience would care specifically. (3) The offer: the $99 Founding Member tier (Founder Rate Pro forever + name engraved + direct email line for the first year + all menus) in exchange for one organic post or story mention. No paid arrangements until D30 retention crosses 25% per the standing CEO constraint.

---

## In-app teaser (existing app users)

If the app has even a small live-tester base, that's the warmest source of backers. Hooks:

1. **Splash screen banner** on app open: "Speakeater Kickstarter launches [DATE]. Get the heads-up." Tap deep-links to email signup.
2. **Settings → About → Kickstarter** entry that opens the prelaunch page.
3. **Push notification** the morning of launch to any opted-in users.

Implementation cost: 30 minutes of code, all of which can ship in the version 0.1.52 build that's queued.

---

## Tracking

Set up before email capture goes live:

- Email service: Buttondown, Mailchimp, or ConvertKit. Buttondown is the simplest for solo dev. Free up to 100 subs, $9/mo after.
- Analytics: every email link tagged with UTM params (`?utm_source=email&utm_campaign=prelaunch&utm_content=email_3`)
- Kickstarter campaign URL gets the same treatment per outbound source.
- Dashboard: a single Google Sheet with daily signups, source breakdown, and Top 10 referrers. Update every Sunday night.

---

## What can go wrong

**Email list too small.** If by day -7 the list is under 200, push the launch by two weeks and double down on social. Better to launch warm than to die fast.

**No press hits.** Don't count on press. If a hit happens, great; if not, the prelaunch funnel is built to fund the campaign on email + organic social alone.

**A single influencer cancels the day before launch.** Have three Tier 1 outreach attempts in motion at all times so no single dropout breaks the plan.
