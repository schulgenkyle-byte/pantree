// Worker-only "metadata parser" — fetches a TikTok / YouTube URL directly from
// the Worker, scrapes whatever recipe text the page already exposes (description,
// og:description, title, captions track for YouTube), feeds it to Claude Haiku,
// and returns a recipe envelope.
//
// This is the cheap path that ships before the Oracle parser box is deployed.
// It handles ~60-70% of cases (recipes written in the description / available as
// captions). For pure-video recipes with no description text, we mark the link
// failed and the user retries once the real box is live.
//
// Cost: ~$0.006 per URL (Haiku 4.5 — 2K input + 800 output tokens).

const HAIKU_MODEL = 'claude-haiku-4-5-20251001';

const SYSTEM_PROMPT = `You extract structured recipes from text scraped from cooking videos / posts.
Return only valid JSON matching the provided tool. Be conservative — if the text doesn't actually
contain a recipe (no ingredients OR no steps), return ok:false with a short reason.
Ingredients should be normalized: { quantity_text, unit, name }. Steps are an ordered array of strings.
Do not invent ingredients or steps not in the source text.`;

const RECIPE_TOOL = {
  name: 'extract_recipe',
  description: 'Extract a structured recipe from scraped video metadata.',
  input_schema: {
    type: 'object',
    properties: {
      ok: { type: 'boolean' },
      reason: { type: 'string', description: 'Why ok=false. Empty if ok=true.' },
      title: { type: 'string' },
      headnote: { type: 'string', description: 'One-sentence summary, no story.' },
      cuisine: { type: 'string' },
      content_type: { type: 'string', enum: ['food', 'cocktail', 'mocktail'] },
      total_minutes: { type: 'number' },
      serves: { type: 'number' },
      ingredients: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            quantity_text: { type: 'string' },
            unit: { type: 'string' },
            name: { type: 'string' },
          },
          required: ['name'],
        },
      },
      steps: { type: 'array', items: { type: 'string' } },
      confidence: { type: 'number', minimum: 0, maximum: 1 },
    },
    required: ['ok'],
  },
};

// ---- HTML scraping ----

function pickMeta(html, attr, value) {
  // Match <meta property="og:description" content="..."> in any attribute order.
  const re = new RegExp(`<meta[^>]+${attr}=["']${value}["'][^>]+content=["']([^"']+)["']`, 'i');
  const m = html.match(re);
  if (m) return decodeEntities(m[1]);
  // Try reversed attribute order: <meta content="..." property="og:description">
  const re2 = new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+${attr}=["']${value}["']`, 'i');
  const m2 = html.match(re2);
  return m2 ? decodeEntities(m2[1]) : null;
}

function pickTitle(html) {
  const m = html.match(/<title>([^<]+)<\/title>/i);
  return m ? decodeEntities(m[1]) : null;
}

function decodeEntities(s) {
  return String(s)
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&#x27;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)));
}

// TikTok embeds the full description in the SIGI_STATE JSON blob. og:description
// is usually truncated. Pull from SIGI when present.
function tiktokDescriptionFromSigi(html) {
  const m = html.match(/<script id="SIGI_STATE"[^>]*>([\s\S]+?)<\/script>/);
  if (!m) return null;
  try {
    const json = JSON.parse(m[1]);
    const items = json?.ItemModule || {};
    const first = Object.values(items)[0];
    if (first?.desc) return first.desc;
  } catch {}
  return null;
}

// YouTube captions: hit the public timedtext endpoint. No API key needed for
// auto-generated captions on a public video. Returns plain text or null.
async function fetchYouTubeCaptions(videoId) {
  try {
    const url = `https://www.youtube.com/api/timedtext?v=${encodeURIComponent(videoId)}&lang=en&fmt=json3`;
    const r = await fetch(url, {
      headers: { 'user-agent': 'Mozilla/5.0 (compatible; SpeakeaterBot/1.0)' },
      cf: { cacheTtl: 600, cacheEverything: true },
    });
    if (!r.ok) return null;
    const j = await r.json();
    const events = j?.events || [];
    const lines = events
      .flatMap(e => (e.segs || []).map(s => s.utf8 || ''))
      .join('')
      .replace(/\s+/g, ' ')
      .trim();
    return lines || null;
  } catch { return null; }
}

// TikTok oEmbed: official, free, doesn't IP-rate-limit Cloudflare Workers like
// the main page render does. Returns { title, author_name, thumbnail_url, ... }
// where `title` is the full caption text (which is where most recipe creators
// put their ingredient lists).
async function fetchTikTokOEmbed(originalUrl) {
  try {
    const r = await fetch(`https://www.tiktok.com/oembed?url=${encodeURIComponent(originalUrl)}`, {
      headers: { 'user-agent': 'Mozilla/5.0 (compatible; SpeakeaterBot/1.0)' },
      cf: { cacheTtl: 600, cacheEverything: true },
    });
    if (!r.ok) return null;
    return await r.json();
  } catch { return null; }
}

function youtubeIdFromUrl(url) {
  try {
    const u = new URL(url);
    if (u.hostname.endsWith('youtube.com')) return u.searchParams.get('v');
    if (u.hostname === 'youtu.be') return u.pathname.slice(1).split('/')[0] || null;
  } catch {}
  return null;
}

// ---- Main parse ----

/**
 * Parse a single URL → recipe envelope. Never throws.
 * Returns: { ok, envelope?, error?, signalsUsed[] }
 */
export async function parseUrlInline(url, env) {
  const signalsUsed = [];
  let pageText = '';
  let title = null;
  let thumbnail = null;
  let host = '';
  try { host = new URL(url).hostname.toLowerCase(); } catch {}

  // 1. Fetch the page HTML.
  let html = '';
  try {
    const r = await fetch(url, {
      headers: {
        'user-agent': 'Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36',
        'accept': 'text/html,application/xhtml+xml',
      },
      cf: { cacheTtl: 300, cacheEverything: true },
    });
    if (!r.ok) return { ok: false, error: `fetch ${r.status}`, signalsUsed };
    html = await r.text();
    if (html.length > 1_500_000) html = html.slice(0, 1_500_000);
  } catch (e) {
    return { ok: false, error: `fetch failed: ${e.message}`, signalsUsed };
  }

  // 2. Extract whatever the page exposes.
  title = pickTitle(html);
  const ogTitle = pickMeta(html, 'property', 'og:title');
  const ogDesc = pickMeta(html, 'property', 'og:description');
  const metaDesc = pickMeta(html, 'name', 'description');
  thumbnail = pickMeta(html, 'property', 'og:image');

  if (host.endsWith('tiktok.com')) {
    // Try BOTH signal sources, not either/or. SIGI is more complete when
    // present but TikTok is increasingly serving Worker-IP requests a stub
    // shell with no SIGI blob; oEmbed is more resilient. Concatenating both
    // is safe because Claude is told to extract the recipe from whichever
    // text is most complete.
    const sigi = tiktokDescriptionFromSigi(html);
    if (sigi) {
      pageText += `TikTok description:\n${sigi}\n\n`;
      signalsUsed.push('tiktok_sigi');
    }
    const oembed = await fetchTikTokOEmbed(url);
    if (oembed?.title) {
      pageText += `TikTok caption:\n${oembed.title}\n\n`;
      if (oembed.author_name) pageText += `Creator: @${oembed.author_name}\n\n`;
      signalsUsed.push('tiktok_oembed');
    }
    if (!sigi && !oembed?.title) {
      // Both signal sources empty → log so we can spot the pattern in tail.
      console.warn(`tiktok parser: no sigi/oembed for ${url} (page bytes: ${html.length})`);
    }
  }

  if (host.endsWith('youtube.com') || host === 'youtu.be') {
    const vid = youtubeIdFromUrl(url);
    if (vid) {
      const caps = await fetchYouTubeCaptions(vid);
      if (caps) {
        pageText += `YouTube auto-captions:\n${caps.slice(0, 8000)}\n\n`;
        signalsUsed.push('youtube_captions');
      }
    }
  }

  if (ogTitle || title) { pageText += `Title: ${ogTitle || title}\n`; signalsUsed.push('title'); }
  if (ogDesc) { pageText += `og:description: ${ogDesc}\n`; signalsUsed.push('og_description'); }
  if (metaDesc && metaDesc !== ogDesc) { pageText += `meta description: ${metaDesc}\n`; signalsUsed.push('meta_description'); }

  if (pageText.trim().length < 40) {
    console.warn(`parser-stub: insufficient text for ${url} (signals=${signalsUsed.join(',')}, len=${pageText.length})`);
    return {
      ok: false,
      error: 'no recipe text found on the page (likely a video-only post — try again when the full parser is online)',
      signalsUsed,
    };
  }
  console.log(`parser-stub: ${host} signals=${signalsUsed.join(',')} text_len=${pageText.length}`);

  // 3. Hand the scraped text to Claude Haiku to extract structured JSON.
  if (!env.ANTHROPIC_API_KEY) {
    return { ok: false, error: 'extraction unavailable (missing ANTHROPIC_API_KEY)', signalsUsed };
  }

  const userMessage = `Source URL: ${url}\nPlatform: ${host}\n\nScraped text:\n---\n${pageText.trim().slice(0, 12_000)}\n---\n\nExtract the recipe.`;

  let aRes;
  try {
    aRes = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-api-key': env.ANTHROPIC_API_KEY,
        'anthropic-version': '2023-06-01',
      },
      body: JSON.stringify({
        model: HAIKU_MODEL,
        max_tokens: 1500,
        system: SYSTEM_PROMPT,
        tools: [RECIPE_TOOL],
        tool_choice: { type: 'tool', name: 'extract_recipe' },
        messages: [{ role: 'user', content: userMessage }],
      }),
    });
  } catch (e) {
    return { ok: false, error: `claude fetch failed: ${e.message}`, signalsUsed };
  }
  if (!aRes.ok) {
    const body = await aRes.text().catch(() => '');
    return { ok: false, error: `claude ${aRes.status}: ${body.slice(0, 200)}`, signalsUsed };
  }

  let claudeJson;
  try { claudeJson = await aRes.json(); }
  catch (e) { return { ok: false, error: `claude bad json: ${e.message}`, signalsUsed }; }

  const tool = (claudeJson?.content || []).find(c => c.type === 'tool_use' && c.name === 'extract_recipe');
  if (!tool?.input) return { ok: false, error: 'claude returned no tool call', signalsUsed };

  const extracted = tool.input;
  if (!extracted.ok) {
    return { ok: false, error: extracted.reason || 'extraction failed', signalsUsed };
  }
  if (!Array.isArray(extracted.ingredients) || extracted.ingredients.length === 0) {
    return { ok: false, error: 'no ingredients extracted', signalsUsed };
  }
  if (!Array.isArray(extracted.steps) || extracted.steps.length === 0) {
    return { ok: false, error: 'no steps extracted', signalsUsed };
  }

  signalsUsed.push('claude_haiku');

  // Envelope shape MUST match ParseEnvelope in ImportLinksDto.kt — Kotlin
  // kotlinx.serialization throws on missing required fields (url, ok), and
  // the polling ViewModel silently swallows that, looking like an infinite
  // spinner. Wrap the extracted data into the parser-box-compatible shape.
  const platform = host.endsWith('tiktok.com') ? 'tiktok'
    : (host.endsWith('youtube.com') || host === 'youtu.be') ? 'youtube'
    : 'unknown';

  const normalizedIngredients = (extracted.ingredients || []).map(ing => {
    // Map quantity_text → quantity (double) when parseable. Keep unit + name.
    const qText = String(ing.quantity_text || '').trim();
    const qNum = qText ? Number(qText.match(/[\d.]+/)?.[0]) : null;
    return {
      name: String(ing.name || '').trim(),
      quantity: Number.isFinite(qNum) ? qNum : null,
      unit: ing.unit || null,
    };
  }).filter(i => i.name);

  const normalizedSteps = (extracted.steps || []).map(s => ({
    text: typeof s === 'string' ? s : String(s.text || s),
  })).filter(s => s.text);

  return {
    ok: true,
    signalsUsed,
    envelope: {
      ok: true,
      url,
      platform,
      recipe: {
        title: extracted.title || ogTitle || title || 'Untitled',
        cuisine: extracted.cuisine || null,
        description: extracted.headnote || null,
        prep_minutes: null,
        cook_minutes: extracted.total_minutes || null,
        servings: extracted.serves || null,
        ingredients: normalizedIngredients,
        steps: normalizedSteps,
        image_url: thumbnail || null,
      },
      reason: null,
      error: null,
      extraction_confidence: typeof extracted.confidence === 'number' ? extracted.confidence : 0.6,
      signals_used: signalsUsed,
      warnings: [],
      latency_ms: null,
    },
  };
}
