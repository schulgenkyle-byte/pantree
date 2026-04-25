// Apply trademark_review flags to confirmed CONFLICT cocktails
const { execSync } = require('child_process');

const conflicts = [
  { id: 'cck_3d7b15741e9a5a35037cb9', title: 'Sazerac',                            note: 'TESS hit: Sazerac Brands LLC - reg# 0602218 (alcoholic cocktails, Class 33, registered 1955)' },
  { id: 'cck_492dc62684517df8e92075', title: 'Sazerac (cocktail)',                 note: 'TESS hit: Sazerac Brands LLC - reg# 0602218 (alcoholic cocktails, Class 33, registered 1955)' },
  { id: 'cck_74eca01a9204553e72d730', title: 'Bahama Mama',                        note: 'TESS hit: Goombay IP Holdings LLC - active TM for alcoholic carbonated drinks (Class 33)' },
  { id: 'cck_12899f82974a478347d04d', title: 'Honey deuce',                        note: 'TESS hit: USTA Inc - filed 2024 (pending) - Grey Goose holds exclusive license; official US Open cocktail' },
  { id: 'cck_0e78f45ca61f5b78703415', title: 'Bacardi cocktail',                   note: 'TESS hit: Bacardi Limited - 1936 NY Supreme Court ruling enforced; must contain Bacardi rum to use name' },
  { id: 'cck_98f319d7737e9b66d85276', title: "Pimm's cocktail",                    note: 'TESS hit: Diageo (Pimm’s brand TM since 1912) - cocktail name embeds protected brand mark' },
  { id: 'cck_818e257f35c49bc6378dea', title: "Pimm's cup",                         note: 'TESS hit: Diageo (Pimm’s brand TM since 1912) - cocktail name embeds protected brand mark' },
  { id: 'cck_f75d0b4c01c2215ed51201', title: 'Homemade Kahlua',                    note: 'TESS hit: The Absolut Company AB / Pernod Ricard - reg# 0711952 (Kahlua, Class 33); Pernod actively litigates' },
  { id: 'cck_ef7da151509b8346c9664d', title: 'Cheeky Vimto',                       note: 'TESS hit: Nichols plc (Vimto brand) - Class 32; bars rename drink to avoid Vimto IP per industry sources' },
  { id: 'cck_89d22dd4b4e5312bfef3d8', title: "Empellón Cocina's Fat-Washed Mezcal", note: 'TESS hit: Alex Stupak / Empellón NYC restaurant TM (Class 43); title literally embeds restaurant brand' },
  { id: 'cck_0f18fc872e145f2b934618', title: 'Bob Marley',                         note: 'TESS hit: Fifty-Six Hope Road Music Ltd (Marley estate) - holds BOB MARLEY/MARLEY marks across classes; estate has 400+ C&Ds, 20+ lawsuits, $2.4M judgment vs Jammin Java' },
];

let ok = 0, fail = 0;
for (const c of conflicts) {
  const noteEsc = c.note.replace(/'/g, "''");
  const sql = `UPDATE recipe SET audit_status = 'trademark_review', audit_notes = '${noteEsc}' WHERE id = '${c.id}'`;
  try {
    const out = execSync(`npx wrangler d1 execute pantrie-db-staging --remote --command "${sql}"`, {
      cwd: __dirname, encoding: 'utf8', maxBuffer: 5 * 1024 * 1024,
    });
    const wroteRow = /1 row|changes":\s*1/.test(out) || out.includes('Executed');
    console.log(`OK  ${c.id}  ${c.title}`);
    ok++;
  } catch (e) {
    console.error(`FAIL ${c.id}  ${c.title}: ${e.message.slice(0,200)}`);
    fail++;
  }
}
console.log(`\nDone: ${ok} ok, ${fail} failed of ${conflicts.length} total`);
