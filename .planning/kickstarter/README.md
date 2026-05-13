# Speakeater Kickstarter — Campaign Materials

Drafted 2026-05-12. Pre-launch Android app campaign.

## Files in this directory

| File | Purpose | Who reads it |
|---|---|---|
| `00-STRATEGY.md` | Why-we're-doing-this, target backer, success thresholds, risks | Internal — Kyle, advisors |
| `01-CAMPAIGN-PAGE.md` | The actual pitch you paste into Kickstarter | Public — backers |
| `02-REWARD-TIERS.md` | Nine tiers + three stretch goals + pricing logic | Public — backers |
| `03-CURATE-A-PARTY-SPEC.md` | Product/eng spec for the new feature | Internal — Kyle as developer |
| `04-SAMPLE-MENUS/` | Five fully-written sample menus that anchor the feature | Both — used in campaign + first ship |
| `05-VIDEO-SCRIPT.md` | 90-second hero video script + shot list | Internal — production |
| `06-PRELAUNCH-FUNNEL.md` | Email captures, social cadence, in-app teaser | Internal — Kyle as marketer |
| `07-LAUNCH-SEQUENCE.md` | Day-by-day playbook from Day -14 through Day 30 | Internal — Kyle |

## What's done

- Strategy locked: $15k goal, 30-day campaign, Prohibition-only with World Tour stretch, digital + 1 physical tier
- Full campaign copy written in Speakeater voice (period-stop, founder-singular, numbers > adjectives, two registers separated)
- 9-tier reward ladder with pricing math
- 5 sample menus complete (~2,500 words each); cover Bee's Knees Garden Party, Speakeasy Opening Night, Roaring Rooftop, A Gatsby Affair, Bootlegger's Den
- Curate-a-Party feature spec including D1 schema, backend endpoints, Path A/Path B pricing recommendation
- Hero video script (works for both live shoot and Remotion render production paths)
- 14-day prelaunch funnel with 5-email sequence + social cadence + influencer outreach plan
- 30-day launch playbook with daily tactics + failure protocols + mental health discipline

## What's not done (and needs to happen before launch)

- Hero video shot OR rendered (Path A live or Path B Remotion — pick one this week)
- speakeater.com/kickstarter prelaunch page wired up
- Email service chosen + configured (Buttondown recommended)
- Hero image + 3-5 in-page mockup images for the KS page
- Bee's Knees menu PDF generated for the email-capture giveaway
- 25 influencer/press accounts identified by name with contact info
- Kickstarter project draft created in Kickstarter's editor and submitted for review
- Founder Rate backer grant script on backend (mints SPEAK-XXXX-YYYY codes per KS backer tier; codes redeemable in-app via /menus/redeem and the entitlement table records `kickstarter_t0X_*` granted_tier for the grandfathered $30/yr rate)
- In-app "Coming to Kickstarter" banner shipped in version 0.1.52

## What I won't do in this session

- The hero video shoot
- All 50 menus (5 are written; the rest are scheduled at 10/month post-campaign)
- Build the Curate-a-Party feature itself (the campaign FUNDS that build)
- Hero image / visual assets (need designer or Speakeater Studio Remotion templates)
- Kickstarter project setup (you do this in Kickstarter's web editor)
- Email service configuration

## Recommended next steps (in order)

1. Read `00-STRATEGY.md` and disagree with anything you want changed
2. Read `01-CAMPAIGN-PAGE.md` out loud — does it sound like you?
3. Pick Path A vs Path B for the video (see `05-VIDEO-SCRIPT.md`)
4. Set up Buttondown + speakeater.com/kickstarter capture page
5. Generate the Bee's Knees PDF from `04-SAMPLE-MENUS/01-bees-knees-garden-party.md`
6. Identify 25 outreach targets and put their contacts in a spreadsheet
7. Build the in-app "Coming to Kickstarter" banner in the 0.1.52 release
8. Lock the launch date

This material is reusable. The story doesn't change between draft and launch. Iterate on the campaign page copy as you talk to potential backers in your network; their objections will sharpen the FAQ.
