// Speakeater Library — three-level hierarchy: Library → Books → Chapters → Recipes
//
// Every user has ONE Library, auto-bootstrapped on first read. The Library has
// 2 STANDARD Books that always exist:
//   - "Saved"     (kind='saved')      — saved/swiped Speakeater recipes
//   - "My Recipes" (kind='my_recipes') — recipes the user authored (submissions, photo-to-recipe, link-import)
//
// Standard Books cannot be deleted or renamed and stay private. Users add
// custom Books (e.g. "Indian", "Date Night Cocktails") with their own Chapters.
//
// Export rules — strict:
//   - Only recipes WHERE recipe.created_by_user_id = requesting userId are
//     included in exports. Saved Speakeater corpus recipes are filtered.
//   - The export response reports both counts so the UI can say
//     "exported 12 of 31 recipes (19 Speakeater corpus recipes filtered out)".
//   - Public viewers see all recipes in a book, but cannot export at all.

import { json, err, uid, validString, validStringOrNull } from './util.js';

// HMAC-SHA256 sign for share-token minting + verification.
async function hmacSign(secret, data) {
  const key = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  return new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data)));
}

function base64UrlEncode(bytes) {
  let str = '';
  for (let i = 0; i < bytes.length; i++) str += String.fromCharCode(bytes[i]);
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64UrlDecode(s) {
  const pad = '='.repeat((4 - s.length % 4) % 4);
  const norm = (s + pad).replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(norm);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

async function verifyShareToken(env, token, expectedBookId) {
  if (!token || typeof token !== 'string' || !token.includes('.')) return null;
  const [bodyB64, sigB64] = token.split('.');
  if (!bodyB64 || !sigB64) return null;
  const bodyBytes = base64UrlDecode(bodyB64);
  const body = new TextDecoder().decode(bodyBytes);
  const expected = await hmacSign(env.JWT_SECRET, body);
  const got = base64UrlDecode(sigB64);
  if (expected.length !== got.length) return null;
  // Constant-time comparison.
  let mismatch = 0;
  for (let i = 0; i < expected.length; i++) mismatch |= expected[i] ^ got[i];
  if (mismatch !== 0) return null;
  const [userId, bookId, expiresStr] = body.split('.');
  if (!userId || !bookId || !expiresStr) return null;
  if (bookId !== expectedBookId) return null;
  if (Date.now() > parseInt(expiresStr, 10)) return null;
  return userId;
}

const VALID_VISIBILITY = new Set(['private', 'unlisted', 'public']);
const MAX_TITLE = 80;
const MAX_DESCRIPTION = 1200;
const MAX_NOTE = 600;
const MAX_BOOKS_PER_LIBRARY = 100;
const MAX_CHAPTERS_PER_BOOK = 40;
const MAX_RECIPES_PER_CHAPTER = 200;

const STANDARD_BOOKS = [
  { kind: 'saved',      title: 'Saved',       slug: 'saved',       defaultChapter: 'Recipes' },
  { kind: 'my_recipes', title: 'My Recipes',  slug: 'my-recipes',  defaultChapter: 'Drafts' },
];

function slugify(s) {
  return String(s).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 60) || 'book';
}

async function uniqueBookSlug(env, libraryUserId, base) {
  let candidate = base;
  for (let i = 0; i < 10; i++) {
    const row = await env.DB.prepare(
      'SELECT 1 FROM book WHERE library_user_id = ? AND slug = ?'
    ).bind(libraryUserId, candidate).first();
    if (!row) return candidate;
    candidate = `${base}-${Math.random().toString(36).slice(2, 6)}`;
  }
  return `${base}-${uid().slice(-6)}`;
}

async function ensureLibrary(env, userId) {
  const existing = await env.DB.prepare('SELECT user_id FROM library WHERE user_id = ?').bind(userId).first();
  if (existing) return false;

  const now = Date.now();
  await env.DB.prepare('INSERT INTO library (user_id, created_at) VALUES (?, ?)').bind(userId, now).run();

  for (let i = 0; i < STANDARD_BOOKS.length; i++) {
    const std = STANDARD_BOOKS[i];
    const bookId = uid();
    await env.DB.prepare(
      `INSERT INTO book (id, library_user_id, slug, title, visibility, is_standard, standard_kind, position, created_at, updated_at)
       VALUES (?, ?, ?, ?, 'private', 1, ?, ?, ?, ?)`
    ).bind(bookId, userId, std.slug, std.title, std.kind, i, now, now).run();

    const chapterId = uid();
    await env.DB.prepare(
      'INSERT INTO chapter (id, book_id, title, position, created_at) VALUES (?, ?, ?, 0, ?)'
    ).bind(chapterId, bookId, std.defaultChapter, now).run();
  }
  return true;
}

/**
 * Auto-add a recipe into the user's standard Book (Saved or My Recipes) at
 * its default chapter. Idempotent: silently skips if the recipe is already
 * pinned in that chapter. Bootstraps the library if missing.
 *
 * Called from:
 *   recipes.interact() when status='saved' (Saved book)
 *   submissions.approve() (My Recipes book — submitter)
 *
 * Failures are non-fatal; logged but don't break the calling flow.
 */
export async function addRecipeToStandardBook(env, userId, standardKind, recipeId) {
  if (!env || !userId || !standardKind || !recipeId) return;
  try {
    await ensureLibrary(env, userId);
    const book = await env.DB.prepare(
      `SELECT id FROM book WHERE library_user_id = ? AND standard_kind = ? AND is_archived = 0 LIMIT 1`
    ).bind(userId, standardKind).first();
    if (!book) return;
    // Use the first chapter (lowest position).
    const chapter = await env.DB.prepare(
      `SELECT id, recipe_count FROM chapter WHERE book_id = ? ORDER BY position ASC LIMIT 1`
    ).bind(book.id).first();
    if (!chapter) return;
    // Idempotent: skip if already present.
    const existing = await env.DB.prepare(
      `SELECT 1 FROM chapter_recipe WHERE chapter_id = ? AND recipe_id = ?`
    ).bind(chapter.id, recipeId).first();
    if (existing) return;

    const now = Date.now();
    await env.DB.prepare(
      `INSERT INTO chapter_recipe (chapter_id, recipe_id, position, added_at) VALUES (?, ?, ?, ?)`
    ).bind(chapter.id, recipeId, chapter.recipe_count, now).run();
    await env.DB.prepare(
      `UPDATE chapter SET recipe_count = recipe_count + 1 WHERE id = ?`
    ).bind(chapter.id).run();
    await env.DB.prepare(
      `UPDATE book SET recipe_count = recipe_count + 1, updated_at = ? WHERE id = ?`
    ).bind(now, book.id).run();
  } catch (e) {
    // Non-fatal: the calling flow (save / approve) succeeds even if Library wiring fails.
    console.warn(`[library] addRecipeToStandardBook(${userId}, ${standardKind}, ${recipeId}) failed:`, (e && e.message) || e);
  }
}

async function loadBookForUser(env, bookId, userId) {
  const b = await env.DB.prepare(
    `SELECT id, library_user_id, slug, title, description, cover_image_url, visibility,
            is_standard, standard_kind, position, fork_of_id, recipe_count, view_count,
            fork_count, is_archived, archived_reason, created_at, updated_at
     FROM book WHERE id = ?`
  ).bind(bookId).first();
  if (!b) return null;
  if (b.library_user_id === userId) return b;
  if (b.visibility === 'private') return null;
  if (b.is_archived && b.visibility !== 'public') return null;
  return b;
}

async function loadChaptersWithRecipes(env, bookId) {
  const chapters = (await env.DB.prepare(
    'SELECT id, title, position, recipe_count, created_at FROM chapter WHERE book_id = ? ORDER BY position ASC'
  ).bind(bookId).all()).results || [];

  for (const ch of chapters) {
    const recipes = await env.DB.prepare(
      `SELECT cr.recipe_id, cr.position, cr.user_note, cr.added_at,
              r.title, r.cuisine, r.image_url, r.skill_level, r.prep_minutes, r.cook_minutes,
              r.servings, r.created_by_user_id
       FROM chapter_recipe cr
       JOIN recipe r ON r.id = cr.recipe_id
       WHERE cr.chapter_id = ?
       ORDER BY cr.position ASC`
    ).bind(ch.id).all();
    ch.recipes = recipes.results || [];
  }
  return chapters;
}

async function loadAuthoredRecipesFull(env, bookId, userId) {
  // For export only — full recipe payload, filtered to user-authored recipes.
  const rows = await env.DB.prepare(
    `SELECT cr.chapter_id, cr.position AS chapter_position, cr.user_note, cr.added_at,
            ch.title AS chapter_title, ch.position AS chapter_order,
            r.id AS recipe_id, r.title, r.cuisine, r.description, r.skill_level,
            r.prep_minutes, r.cook_minutes, r.servings, r.image_url, r.attribution,
            r.created_by_user_id
     FROM chapter_recipe cr
     JOIN chapter ch ON ch.id = cr.chapter_id
     JOIN recipe r   ON r.id  = cr.recipe_id
     WHERE ch.book_id = ? AND r.created_by_user_id = ?
     ORDER BY ch.position ASC, cr.position ASC`
  ).bind(bookId, userId).all();
  const recipes = rows.results || [];
  for (const r of recipes) {
    const ings = await env.DB.prepare(
      'SELECT seq, name, canonical_name, quantity, unit FROM recipe_ingredient WHERE recipe_id = ? ORDER BY seq ASC'
    ).bind(r.recipe_id).all();
    const steps = await env.DB.prepare(
      'SELECT seq, text, timer_seconds FROM recipe_step WHERE recipe_id = ? ORDER BY seq ASC'
    ).bind(r.recipe_id).all();
    r.ingredients = ings.results || [];
    r.steps = steps.results || [];
  }
  return recipes;
}

async function countTotalRecipesInBook(env, bookId) {
  const r = await env.DB.prepare(
    `SELECT COUNT(*) AS n
     FROM chapter_recipe cr JOIN chapter ch ON ch.id = cr.chapter_id
     WHERE ch.book_id = ?`
  ).bind(bookId).first();
  return r ? r.n : 0;
}

export const handleLibrary = {
  // GET /api/library — bootstrap if missing, return library state + all books.
  async get(userId, env, request) {
    await ensureLibrary(env, userId);
    const books = (await env.DB.prepare(
      `SELECT id, slug, title, description, cover_image_url, visibility, is_standard,
              standard_kind, position, fork_of_id, recipe_count, view_count, fork_count,
              created_at, updated_at
       FROM book WHERE library_user_id = ? AND is_archived = 0
       ORDER BY position ASC, updated_at DESC`
    ).bind(userId).all()).results || [];
    return json({ books }, 200, request, env);
  },

  // POST /api/library/books — create a custom book
  async createBook(request, userId, env) {
    await ensureLibrary(env, userId);

    const count = await env.DB.prepare(
      'SELECT COUNT(*) AS n FROM book WHERE library_user_id = ? AND is_archived = 0'
    ).bind(userId).first();
    if (count.n >= MAX_BOOKS_PER_LIBRARY) return err(400, `library is full (${MAX_BOOKS_PER_LIBRARY} books max)`);

    let body; try { body = await request.json(); } catch { return err(400, 'invalid json'); }
    const title = validString(body.title, { min: 1, max: MAX_TITLE });
    if (!title) return err(400, 'title required (1-80 chars)');
    const description = validStringOrNull(body.description, { max: MAX_DESCRIPTION });
    const visibility = VALID_VISIBILITY.has(body.visibility) ? body.visibility : 'private';

    const id = uid();
    const slug = await uniqueBookSlug(env, userId, slugify(title));
    const now = Date.now();
    const position = count.n;

    await env.DB.prepare(
      `INSERT INTO book (id, library_user_id, slug, title, description, visibility, position, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(id, userId, slug, title, description, visibility, position, now, now).run();

    return json({ id, slug, title, description, visibility, position, recipe_count: 0, is_standard: 0 }, 201, request, env);
  },

  // GET /api/library/books/:bookId — book + its chapters + recipes
  async getBook(bookId, userId, env, request) {
    const book = await loadBookForUser(env, bookId, userId);
    if (!book) return err(404, 'book not found');
    const chapters = await loadChaptersWithRecipes(env, bookId);
    return json({ book, chapters }, 200, request, env);
  },

  // PUT /api/library/books/:bookId — update book metadata
  async updateBook(bookId, request, userId, env) {
    const b = await env.DB.prepare(
      'SELECT library_user_id, is_standard FROM book WHERE id = ?'
    ).bind(bookId).first();
    if (!b) return err(404, 'book not found');
    if (b.library_user_id !== userId) return err(403, 'not your book');

    let body; try { body = await request.json(); } catch { return err(400, 'invalid json'); }

    const updates = [], params = [];
    if (typeof body.title === 'string') {
      if (b.is_standard) return err(400, 'standard books cannot be renamed');
      const t = validString(body.title, { min: 1, max: MAX_TITLE });
      if (!t) return err(400, 'title 1-80 chars');
      updates.push('title = ?'); params.push(t);
    }
    if (body.description !== undefined) {
      updates.push('description = ?'); params.push(validStringOrNull(body.description, { max: MAX_DESCRIPTION }));
    }
    if (typeof body.visibility === 'string') {
      if (b.is_standard && body.visibility !== 'private') return err(400, 'standard books are always private');
      if (!VALID_VISIBILITY.has(body.visibility)) return err(400, 'visibility must be private|unlisted|public');
      updates.push('visibility = ?'); params.push(body.visibility);
    }
    if (typeof body.cover_image_url === 'string' || body.cover_image_url === null) {
      updates.push('cover_image_url = ?'); params.push(body.cover_image_url || null);
    }
    if (updates.length === 0) return err(400, 'nothing to update');

    updates.push('updated_at = ?'); params.push(Date.now());
    params.push(bookId);
    await env.DB.prepare(`UPDATE book SET ${updates.join(', ')} WHERE id = ?`).bind(...params).run();
    return json({ ok: true }, 200, request, env);
  },

  // DELETE /api/library/books/:bookId — only non-standard, non-public soft-deletes
  async deleteBook(bookId, userId, env, request) {
    const b = await env.DB.prepare(
      'SELECT library_user_id, is_standard, visibility FROM book WHERE id = ?'
    ).bind(bookId).first();
    if (!b) return err(404, 'book not found');
    if (b.library_user_id !== userId) return err(403, 'not your book');
    if (b.is_standard) return err(400, 'standard books cannot be deleted');

    if (b.visibility === 'private') {
      await env.DB.prepare('DELETE FROM book WHERE id = ?').bind(bookId).run();
    } else {
      await env.DB.prepare(
        `UPDATE book SET is_archived = 1, archived_reason = 'owner_deleted', updated_at = ? WHERE id = ?`
      ).bind(Date.now(), bookId).run();
    }
    return json({ ok: true }, 200, request, env);
  },

  // POST /api/library/books/:bookId/chapters — create a chapter
  async createChapter(bookId, request, userId, env) {
    const b = await env.DB.prepare('SELECT library_user_id FROM book WHERE id = ?').bind(bookId).first();
    if (!b) return err(404, 'book not found');
    if (b.library_user_id !== userId) return err(403, 'not your book');

    const count = await env.DB.prepare('SELECT COUNT(*) AS n FROM chapter WHERE book_id = ?').bind(bookId).first();
    if (count.n >= MAX_CHAPTERS_PER_BOOK) return err(400, `book is full (${MAX_CHAPTERS_PER_BOOK} chapters max)`);

    let body; try { body = await request.json(); } catch { return err(400, 'invalid json'); }
    const title = validString(body.title, { min: 1, max: MAX_TITLE });
    if (!title) return err(400, 'title required');

    const id = uid();
    const now = Date.now();
    await env.DB.prepare(
      'INSERT INTO chapter (id, book_id, title, position, created_at) VALUES (?, ?, ?, ?, ?)'
    ).bind(id, bookId, title, count.n, now).run();
    await env.DB.prepare('UPDATE book SET updated_at = ? WHERE id = ?').bind(now, bookId).run();
    return json({ id, title, position: count.n, recipe_count: 0 }, 201, request, env);
  },

  // PUT /api/library/books/:bookId/chapters/:chapterId — rename / reorder
  async updateChapter(bookId, chapterId, request, userId, env) {
    const ch = await env.DB.prepare(
      `SELECT c.id, c.title, c.position, b.library_user_id
       FROM chapter c JOIN book b ON b.id = c.book_id
       WHERE c.id = ? AND c.book_id = ?`
    ).bind(chapterId, bookId).first();
    if (!ch) return err(404, 'chapter not found');
    if (ch.library_user_id !== userId) return err(403, 'not your chapter');

    let body; try { body = await request.json(); } catch { return err(400, 'invalid json'); }
    const updates = [], params = [];
    if (typeof body.title === 'string') {
      const t = validString(body.title, { min: 1, max: MAX_TITLE });
      if (!t) return err(400, 'title 1-80 chars');
      updates.push('title = ?'); params.push(t);
    }
    if (typeof body.position === 'number') {
      updates.push('position = ?'); params.push(Math.max(0, Math.floor(body.position)));
    }
    if (updates.length === 0) return err(400, 'nothing to update');
    params.push(chapterId);
    await env.DB.prepare(`UPDATE chapter SET ${updates.join(', ')} WHERE id = ?`).bind(...params).run();
    await env.DB.prepare('UPDATE book SET updated_at = ? WHERE id = ?').bind(Date.now(), bookId).run();
    return json({ ok: true }, 200, request, env);
  },

  // DELETE /api/library/books/:bookId/chapters/:chapterId
  async deleteChapter(bookId, chapterId, userId, env, request) {
    const ch = await env.DB.prepare(
      `SELECT c.id, b.library_user_id
       FROM chapter c JOIN book b ON b.id = c.book_id
       WHERE c.id = ? AND c.book_id = ?`
    ).bind(chapterId, bookId).first();
    if (!ch) return err(404, 'chapter not found');
    if (ch.library_user_id !== userId) return err(403, 'not your chapter');

    await env.DB.prepare('DELETE FROM chapter WHERE id = ?').bind(chapterId).run();
    // Recompute book.recipe_count.
    const total = await countTotalRecipesInBook(env, bookId);
    await env.DB.prepare('UPDATE book SET recipe_count = ?, updated_at = ? WHERE id = ?')
      .bind(total, Date.now(), bookId).run();
    return json({ ok: true }, 200, request, env);
  },

  // POST /api/library/books/:bookId/chapters/:chapterId/recipes — add a recipe
  async addRecipe(bookId, chapterId, request, userId, env) {
    const ch = await env.DB.prepare(
      `SELECT c.id, c.recipe_count, b.library_user_id
       FROM chapter c JOIN book b ON b.id = c.book_id
       WHERE c.id = ? AND c.book_id = ?`
    ).bind(chapterId, bookId).first();
    if (!ch) return err(404, 'chapter not found');
    if (ch.library_user_id !== userId) return err(403, 'not your chapter');
    if (ch.recipe_count >= MAX_RECIPES_PER_CHAPTER) return err(400, `chapter is full (${MAX_RECIPES_PER_CHAPTER} recipes max)`);

    let body; try { body = await request.json(); } catch { return err(400, 'invalid json'); }
    const recipeId = validString(body.recipe_id, { min: 1, max: 64 });
    if (!recipeId) return err(400, 'recipe_id required');
    const userNote = validStringOrNull(body.user_note, { max: MAX_NOTE });

    const exists = await env.DB.prepare('SELECT 1 FROM recipe WHERE id = ?').bind(recipeId).first();
    if (!exists) return err(404, 'recipe not found');

    const now = Date.now();
    try {
      await env.DB.prepare(
        'INSERT INTO chapter_recipe (chapter_id, recipe_id, position, user_note, added_at) VALUES (?, ?, ?, ?, ?)'
      ).bind(chapterId, recipeId, ch.recipe_count, userNote, now).run();
    } catch {
      return err(409, 'recipe already in this chapter');
    }
    await env.DB.prepare('UPDATE chapter SET recipe_count = recipe_count + 1 WHERE id = ?').bind(chapterId).run();
    await env.DB.prepare('UPDATE book SET recipe_count = recipe_count + 1, updated_at = ? WHERE id = ?')
      .bind(now, bookId).run();
    return json({ ok: true, position: ch.recipe_count }, 201, request, env);
  },

  // DELETE /api/library/books/:bookId/chapters/:chapterId/recipes/:recipeId
  async removeRecipe(bookId, chapterId, recipeId, userId, env, request) {
    const ch = await env.DB.prepare(
      `SELECT c.id, b.library_user_id
       FROM chapter c JOIN book b ON b.id = c.book_id
       WHERE c.id = ? AND c.book_id = ?`
    ).bind(chapterId, bookId).first();
    if (!ch) return err(404, 'chapter not found');
    if (ch.library_user_id !== userId) return err(403, 'not your chapter');

    const result = await env.DB.prepare(
      'DELETE FROM chapter_recipe WHERE chapter_id = ? AND recipe_id = ?'
    ).bind(chapterId, recipeId).run();
    if (result.meta && result.meta.changes === 0) return err(404, 'recipe not in this chapter');

    await env.DB.prepare('UPDATE chapter SET recipe_count = MAX(0, recipe_count - 1) WHERE id = ?').bind(chapterId).run();
    await env.DB.prepare('UPDATE book SET recipe_count = MAX(0, recipe_count - 1), updated_at = ? WHERE id = ?')
      .bind(Date.now(), bookId).run();
    return json({ ok: true }, 200, request, env);
  },

  // POST /api/library/books/:bookId/fork — fork a public/unlisted book into your library
  async forkBook(sourceId, userId, env, request) {
    await ensureLibrary(env, userId);
    const source = await env.DB.prepare(
      `SELECT id, slug, title, description, visibility, is_archived FROM book WHERE id = ?`
    ).bind(sourceId).first();
    if (!source) return err(404, 'book not found');
    if (source.visibility === 'private') return err(403, 'cannot fork a private book');
    if (source.is_archived && source.visibility !== 'public') return err(404, 'book unavailable');

    const now = Date.now();
    const newId = uid();
    const newSlug = await uniqueBookSlug(env, userId, slugify(`${source.title}-fork`));

    // Get position = count of user's existing books.
    const cnt = await env.DB.prepare('SELECT COUNT(*) AS n FROM book WHERE library_user_id = ? AND is_archived = 0').bind(userId).first();

    await env.DB.prepare(
      `INSERT INTO book (id, library_user_id, slug, title, description, visibility, fork_of_id, position, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, 'private', ?, ?, ?, ?)`
    ).bind(newId, userId, newSlug, source.title, source.description, sourceId, cnt.n, now, now).run();

    // Copy chapters + chapter_recipes.
    const chapters = (await env.DB.prepare(
      'SELECT id, title, position FROM chapter WHERE book_id = ? ORDER BY position ASC'
    ).bind(sourceId).all()).results || [];
    let totalRecipes = 0;
    for (const srcCh of chapters) {
      const newChId = uid();
      await env.DB.prepare(
        'INSERT INTO chapter (id, book_id, title, position, created_at) VALUES (?, ?, ?, ?, ?)'
      ).bind(newChId, newId, srcCh.title, srcCh.position, now).run();
      const recipes = (await env.DB.prepare(
        'SELECT recipe_id, position, user_note FROM chapter_recipe WHERE chapter_id = ? ORDER BY position ASC'
      ).bind(srcCh.id).all()).results || [];
      for (const r of recipes) {
        await env.DB.prepare(
          'INSERT INTO chapter_recipe (chapter_id, recipe_id, position, user_note, added_at) VALUES (?, ?, ?, ?, ?)'
        ).bind(newChId, r.recipe_id, r.position, r.user_note, now).run();
        totalRecipes++;
      }
      await env.DB.prepare('UPDATE chapter SET recipe_count = ? WHERE id = ?').bind(recipes.length, newChId).run();
    }
    await env.DB.prepare('UPDATE book SET recipe_count = ?, updated_at = ? WHERE id = ?').bind(totalRecipes, now, newId).run();
    await env.DB.prepare('UPDATE book SET fork_count = fork_count + 1 WHERE id = ?').bind(sourceId).run();

    return json({ id: newId, slug: newSlug, fork_of_id: sourceId, recipe_count: totalRecipes }, 201, request, env);
  },

  // POST /api/library/books/:bookId/share-token — mint a 5-minute share token
  // the system browser can carry as a URL query param. Owner-only.
  async mintShareToken(bookId, userId, env, request) {
    const book = await env.DB.prepare(
      'SELECT id, owner_user_id FROM book WHERE id = ?'
    ).bind(bookId).first();
    if (!book) return err(404, 'book not found');
    if (book.owner_user_id !== userId) return err(403, 'not your book');

    const expiresAt = Date.now() + 5 * 60 * 1000; // 5 minutes
    const tokenBody = `${userId}.${bookId}.${expiresAt}`;
    // HMAC-SHA256 sign with JWT_SECRET (already in env, used by auth).
    const sigBytes = await hmacSign(env.JWT_SECRET, tokenBody);
    const sig = base64UrlEncode(sigBytes);
    const token = `${base64UrlEncode(new TextEncoder().encode(tokenBody))}.${sig}`;

    return json({ token, expires_at: expiresAt }, 200, request, env);
  },

  // GET /api/library/books/:bookId/export?format=json|markdown — owner-only,
  // filtered to user-authored recipes. Accepts either Authorization: Bearer
  // (normal auth path) OR ?share=<token> (browser-friendly download path).
  async exportBook(bookId, userId, env, request) {
    const book = await env.DB.prepare(
      'SELECT id, library_user_id, slug, title, description, visibility, recipe_count, created_at, updated_at FROM book WHERE id = ?'
    ).bind(bookId).first();
    if (!book) return err(404, 'book not found');
    // Ownership check — accept either authed userId OR a valid share-token in the URL.
    let effectiveUserId = userId;
    if (book.library_user_id !== userId) {
      const url = new URL(request.url);
      const shareToken = url.searchParams.get('share');
      if (shareToken) {
        const tokenUserId = await verifyShareToken(env, shareToken, bookId);
        if (tokenUserId === book.library_user_id) {
          effectiveUserId = tokenUserId;
        } else {
          return err(403, 'invalid or expired share token');
        }
      } else {
        return err(403, 'export only available to the book owner');
      }
    }

    const url = new URL(request.url);
    const format = url.searchParams.get('format') || 'json';
    if (!['json', 'markdown'].includes(format)) return err(400, 'format must be json or markdown');

    const totalInBook = await countTotalRecipesInBook(env, bookId);
    const authored = await loadAuthoredRecipesFull(env, bookId, effectiveUserId);
    const filteredCount = totalInBook - authored.length;

    // Audit log.
    await env.DB.prepare(
      'INSERT INTO book_export_log (book_id, user_id, format, recipe_count_exported, recipe_count_filtered, exported_at) VALUES (?, ?, ?, ?, ?, ?)'
    ).bind(bookId, effectiveUserId, format, authored.length, filteredCount, Date.now()).run();

    if (format === 'json') {
      const payload = {
        format: 'speakeater-book-v1',
        ownership_note: 'This export contains ONLY recipes you authored. Saved recipes from the Speakeater corpus are filtered out per the Speakeater data-ownership policy.',
        book: {
          id: book.id,
          slug: book.slug,
          title: book.title,
          description: book.description,
          recipe_count_in_book: totalInBook,
          recipe_count_exported: authored.length,
          recipe_count_filtered: filteredCount,
        },
        recipes: authored.map(r => ({
          id: r.recipe_id,
          chapter: r.chapter_title,
          title: r.title,
          cuisine: r.cuisine,
          description: r.description,
          skill_level: r.skill_level,
          prep_minutes: r.prep_minutes,
          cook_minutes: r.cook_minutes,
          servings: r.servings,
          image_url: r.image_url,
          attribution: r.attribution,
          user_note: r.user_note,
          ingredients: r.ingredients,
          steps: r.steps,
        })),
        exported_at: new Date().toISOString(),
      };
      return new Response(JSON.stringify(payload, null, 2), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          'Content-Disposition': `attachment; filename="${book.slug}.json"`,
        },
      });
    }

    // Markdown
    const lines = [];
    lines.push(`# ${book.title}`);
    lines.push('');
    if (book.description) { lines.push(book.description); lines.push(''); }
    lines.push(`_${authored.length} of ${totalInBook} recipes exported · ${filteredCount} Speakeater corpus recipes filtered (you can only export recipes you authored)_`);
    lines.push('');
    lines.push(`_Exported ${new Date().toISOString().slice(0, 10)} from speakeater.com_`);
    lines.push('');

    let lastChapter = null;
    for (const r of authored) {
      if (r.chapter_title !== lastChapter) {
        lines.push('---');
        lines.push('');
        lines.push(`## ${r.chapter_title}`);
        lines.push('');
        lastChapter = r.chapter_title;
      }
      lines.push(`### ${r.title}`);
      lines.push('');
      const meta = [];
      if (r.cuisine) meta.push(r.cuisine);
      if (r.servings) meta.push(`Serves ${r.servings}`);
      if (r.prep_minutes) meta.push(`Prep ${r.prep_minutes} min`);
      if (r.cook_minutes) meta.push(`Cook ${r.cook_minutes} min`);
      if (meta.length) { lines.push(`_${meta.join(' · ')}_`); lines.push(''); }
      if (r.user_note) { lines.push(`> ${r.user_note}`); lines.push(''); }
      if (r.description) { lines.push(r.description); lines.push(''); }
      lines.push('**Ingredients**');
      lines.push('');
      for (const ing of r.ingredients || []) {
        const qty = ing.quantity ? `${ing.quantity}${ing.unit ? ' ' + ing.unit : ''} ` : '';
        lines.push(`- ${qty}${ing.name}`);
      }
      lines.push('');
      lines.push('**Steps**');
      lines.push('');
      for (const step of r.steps || []) lines.push(`${step.seq}. ${step.text}`);
      lines.push('');
    }

    if (authored.length === 0) {
      lines.push('_This book contains no recipes you authored. Saved recipes from the Speakeater corpus cannot be exported. To export recipes, submit your own via Photo to Recipe or Link Import._');
      lines.push('');
    }

    return new Response(lines.join('\n'), {
      status: 200,
      headers: {
        'Content-Type': 'text/markdown; charset=utf-8',
        'Content-Disposition': `attachment; filename="${book.slug}.md"`,
      },
    });
  },

  // GET /b/:bookId — public read (anyone with link). NO AUTH.
  async getPublicBook(bookId, env, request) {
    const book = await env.DB.prepare(
      `SELECT id, library_user_id, slug, title, description, cover_image_url, visibility,
              fork_of_id, recipe_count, view_count, fork_count, is_archived,
              created_at, updated_at
       FROM book WHERE id = ?`
    ).bind(bookId).first();
    if (!book) return err(404, 'book not found');
    if (book.visibility === 'private') return err(404, 'book not found');
    if (book.is_archived && book.visibility !== 'public') return err(404, 'book unavailable');

    env.DB.prepare('UPDATE book SET view_count = view_count + 1 WHERE id = ?').bind(book.id).run().catch(() => {});
    const chapters = await loadChaptersWithRecipes(env, book.id);
    return json({ book, chapters }, 200, request, env);
  },

  // GET /b/:bookId.html — server-rendered public HTML (search-indexable, sharable).
  // Use a small inline-template approach so we don't pull a templating dependency.
  async getPublicBookHtml(bookId, env, request) {
    const book = await env.DB.prepare(
      `SELECT id, slug, title, description, cover_image_url, visibility,
              fork_of_id, recipe_count, view_count, fork_count, is_archived
       FROM book WHERE id = ?`
    ).bind(bookId).first();
    if (!book || book.visibility === 'private' || (book.is_archived && book.visibility !== 'public')) {
      return new Response(notFoundHtml(), { status: 404, headers: { 'Content-Type': 'text/html; charset=utf-8' } });
    }
    env.DB.prepare('UPDATE book SET view_count = view_count + 1 WHERE id = ?').bind(book.id).run().catch(() => {});
    const chapters = await loadChaptersWithRecipes(env, book.id);
    const html = renderBookHtml(book, chapters);
    return new Response(html, {
      status: 200,
      headers: {
        'Content-Type': 'text/html; charset=utf-8',
        'Cache-Control': 'public, max-age=300',
      },
    });
  },
};

// =============================================================================
// Server-rendered cookbook HTML — speakeater.com/b/:bookId.html
// Speakeasy-mode brand: #0D0D0E bg, #C9A554 brass, #E8E3D9 cream ink.
// =============================================================================
function escapeHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function notFoundHtml() {
  return `<!doctype html><html><head><meta charset="utf-8"><title>Cookbook not found</title><meta name="robots" content="noindex"></head><body style="background:#0D0D0E;color:#E8E3D9;font-family:system-ui;padding:48px;text-align:center"><h1 style="color:#C9A554">Speakeater</h1><p>This cookbook is private or has been removed.</p></body></html>`;
}

function renderBookHtml(book, chapters) {
  const title = escapeHtml(book.title);
  const description = escapeHtml(book.description || '');
  const recipeCount = book.recipe_count;
  const og = book.cover_image_url || '';

  const chapterHtml = chapters.map((ch) => {
    const recipes = (ch.recipes || []).map((r) => `
      <li class="recipe">
        <div class="rcard">
          ${r.imageUrl || r.image_url ? `<div class="thumb" style="background-image:url('${escapeHtml(r.image_url || r.imageUrl)}')"></div>` : `<div class="thumb thumb--empty"></div>`}
          <div class="rmeta">
            <h3>${escapeHtml(r.title)}</h3>
            ${r.cuisine ? `<p class="cuisine">${escapeHtml(r.cuisine)}</p>` : ''}
            ${r.user_note ? `<p class="note">${escapeHtml(r.user_note)}</p>` : ''}
          </div>
        </div>
      </li>
    `).join('');
    return `
      <section class="chapter">
        <h2>${escapeHtml(ch.title)} <span class="count">${ch.recipe_count}</span></h2>
        <ul class="recipes">${recipes}</ul>
      </section>
    `;
  }).join('');

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title} · Speakeater</title>
  <meta name="description" content="${description || `${recipeCount} recipes in this Speakeater cookbook.`}">
  <meta property="og:title" content="${title}">
  <meta property="og:description" content="${description}">
  ${og ? `<meta property="og:image" content="${escapeHtml(og)}">` : ''}
  <meta property="og:type" content="website">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Fraunces:wght@400;500;600&family=Mulish:wght@400;500;600;700&display=swap" rel="stylesheet">
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{background:#0D0D0E;color:#E8E3D9;font-family:'Mulish',system-ui,sans-serif;line-height:1.6;min-height:100vh}
    .container{max-width:1080px;margin:0 auto;padding:48px 24px}
    .brand{font-family:'Fraunces',serif;font-size:14px;letter-spacing:3px;text-transform:uppercase;color:#C9A554;margin-bottom:32px;font-weight:500}
    .brand a{color:inherit;text-decoration:none}
    h1{font-family:'Fraunces',serif;font-size:48px;font-weight:500;letter-spacing:-1px;margin-bottom:12px;line-height:1.1}
    .description{font-size:17px;color:rgba(232,227,217,0.62);max-width:640px;margin-bottom:24px}
    .stats{display:flex;gap:16px;font-size:13px;color:rgba(232,227,217,0.38);margin-bottom:48px;border-bottom:1px solid rgba(232,227,217,0.10);padding-bottom:24px}
    .stats .dot{color:#C9A554}
    .chapter{margin-bottom:48px}
    .chapter h2{font-family:'Fraunces',serif;font-size:28px;font-weight:500;margin-bottom:16px;color:#E8E3D9;display:flex;align-items:baseline;gap:12px}
    .chapter h2 .count{font-size:14px;color:rgba(232,227,217,0.38);font-weight:400}
    .recipes{list-style:none;display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:14px}
    .rcard{background:#161214;border:1px solid rgba(232,227,217,0.08);border-radius:10px;overflow:hidden;border-top:2px solid #C9A554}
    .thumb{width:100%;aspect-ratio:16/9;background-size:cover;background-position:center;background-color:#1F1A1C}
    .thumb--empty{background:linear-gradient(135deg,#1F1A1C 0%,#161214 100%)}
    .rmeta{padding:14px 16px}
    .rmeta h3{font-family:'Fraunces',serif;font-size:18px;font-weight:500;color:#E8E3D9;margin-bottom:4px}
    .cuisine{font-size:12px;color:rgba(232,227,217,0.62);text-transform:uppercase;letter-spacing:1px}
    .note{font-size:13px;color:rgba(232,227,217,0.62);margin-top:8px;font-style:italic}
    .footer{margin-top:64px;padding-top:32px;border-top:1px solid rgba(232,227,217,0.10);text-align:center;font-size:13px;color:rgba(232,227,217,0.38)}
    .footer a{color:#C9A554;text-decoration:none;font-weight:600}
    .footer a:hover{text-decoration:underline}
    @media (max-width:640px){h1{font-size:36px}.container{padding:32px 16px}}
  </style>
</head>
<body>
  <div class="container">
    <div class="brand"><a href="https://speakeater.com">Speakeater</a></div>
    <h1>${title}</h1>
    ${description ? `<p class="description">${description}</p>` : ''}
    <div class="stats">
      <span><b>${recipeCount}</b> recipes</span>
      <span class="dot">·</span>
      <span><b>${book.view_count}</b> views</span>
      ${book.fork_count > 0 ? `<span class="dot">·</span><span><b>${book.fork_count}</b> forks</span>` : ''}
    </div>
    ${chapterHtml || `<p style="color:rgba(232,227,217,0.62);font-style:italic">No chapters yet.</p>`}
    <div class="footer">
      <p>Curated on <a href="https://speakeater.com">Speakeater</a> — the cooking app that reads your fridge.</p>
    </div>
  </div>
</body>
</html>`;
}
