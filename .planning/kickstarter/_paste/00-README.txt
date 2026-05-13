PASTE INTO KICKSTARTER

TEXT TO PASTE (in this order, into Kickstarter's pitch editor):

  1. Story body            -> 01-story.txt
  2. Risks and challenges  -> 02-risks.txt
  3. FAQs (8 total)        -> 03-faqs/*.txt (one per FAQ, or 03-faqs/_ALL.txt)
  4. Reward tiers (11)      -> 04-rewards/*.txt (one per tier)
  5. Stretch goals         -> 04-rewards/_stretch-goals.txt

Each .txt is plain text. Paragraph breaks preserved. No markdown syntax to clean up.
Open file, Ctrl+A, Ctrl+C, paste into the matching Kickstarter field.

IMAGES TO UPLOAD INSIDE KICKSTARTER'S EDITOR:

  kickstarter-hero.png         Campaign banner (top of page + social previews)
  tier-images/ (11 PNGs)         Card art for the 11 reward tiers — one per tier
  app-screens-NEW/             33 renamed app screenshots, organized by section
  IMAGE_UPLOAD_CHECKLIST.txt   Step-by-step list of which app screen to upload where

VISUAL PREVIEWS (open in a browser, do not paste):

  01-PITCH-BODY-PASTE-READY.html           Single file: full story with images, paste-ready into KS Story
  01-story-with-app-screens.html           Linked-image preview (faster to open)
  01-story-with-app-screens-EMBEDDED.html  Same, base64-embedded for sharing (~23 MB)
  01-story-with-images.html                Legacy AI-hero-image preview

BUILD COMMANDS (re-run from .planning/kickstarter/):

  node _build_paste_folder.cjs                  Rebuild this folder from the .md sources
  node _build_story_with_app_screens.cjs        Rebuild the linked-image preview
  node _build_story_with_app_screens_embedded.cjs  Rebuild base64-embedded preview
  node _build_pitch_body_paste_ready.cjs        Rebuild the paste-ready pitch body
  node _build_upload_checklist.cjs              Rebuild the upload checklist