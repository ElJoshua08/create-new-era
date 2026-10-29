// Copies the mod's recipes and the item icons they need into the docs site, so recipe pages
// always show what the mod's data files say. Run from docs-site/ after a recipe changes:
//
//   VANILLA_ASSETS=<extracted minecraft jar>/assets CREATE_ASSETS=<extracted create jar>/assets \
//     node scripts/sync-recipes.mjs
//
// Both variables are only needed for icons that aren't already in public/img/items.
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const repo = join(import.meta.dirname, '..', '..');
const recipeDir = join(repo, 'src/main/resources/data/create_new_era/recipe');
const modTextures = join(repo, 'src/main/resources/assets/create_new_era/textures/item');
const renders = join(repo, 'website/img/items');
const out = join(import.meta.dirname, '..', 'public/img/items');
const vanilla = process.env.VANILLA_ASSETS;
const create = process.env.CREATE_ASSETS;

const enLang = JSON.parse(
  readFileSync(join(repo, 'src/generated/resources/assets/create_new_era/lang/en_us.json'), 'utf8'),
);
const esLang = JSON.parse(readFileSync(join(repo, 'src/main/resources/assets/create_new_era/lang/es_es.json'), 'utf8'));

// names of the items from Minecraft and Create that the recipes use, from their lang files
const names = {
  'minecraft:glass_pane': ['Glass Pane', 'Panel de cristal'],
  'minecraft:copper_ingot': ['Copper Ingot', 'Lingote de cobre'],
  'minecraft:stone_button': ['Stone Button', 'Botón de piedra'],
  'minecraft:red_dye': ['Red Dye', 'Tinte rojo'],
  'minecraft:blue_dye': ['Blue Dye', 'Tinte azul'],
  'minecraft:lever': ['Lever', 'Palanca'],
  'minecraft:glowstone_dust': ['Glowstone Dust', 'Polvo de piedra luminosa'],
  'minecraft:iron_nugget': ['Iron Nugget', 'Pepita de hierro'],
  'create:copper_sheet': ['Copper Sheet', 'Lámina de cobre'],
  'create:brass_sheet': ['Brass Sheet', 'Lámina de latón'],
  'create:electron_tube': ['Electron Tube', 'Tubo de electrones'],
  'create:precision_mechanism': ['Precision Mechanism', 'Mecanismo de precisión'],
  'create:andesite_alloy': ['Andesite Alloy', 'Aleación de andesita'],
  'create:brass_casing': ['Brass Casing', 'Revestimiento de latón'],
  'create:andesite_casing': ['Andesite Casing', 'Revestimiento de andesita'],
  'create:mechanical_bearing': ['Mechanical Bearing', 'Rodamiento mecánico'],
  'create:hand_crank': ['Hand Crank', 'Manivela'],
  'create:nixie_tube': ['Nixie Tube', 'Tubo Nixie'],
  'create:speedometer': ['Speedometer', 'Velocímetro'],
};

function nameOf(id) {
  if (names[id]) return names[id];
  const [ns, path] = id.split(':');
  for (const kind of ['block', 'item']) {
    const key = `${kind}.${ns}.${path}`;
    if (enLang[key]) return [enLang[key], esLang[key] ?? enLang[key]];
  }
  throw new Error(`no name for ${id}`);
}

mkdirSync(out, { recursive: true });

function icon(id) {
  const [ns, path] = id.split(':');
  const file = `${ns}_${path}.png`;
  const target = join(out, file);
  const candidates = [
    join(renders, file),
    ns === 'create_new_era' && join(modTextures, `${path}.png`),
    ns === 'create' && create && join(create, 'create/textures/item', `${path}.png`),
    ns === 'minecraft' && vanilla && join(vanilla, 'minecraft/textures/item', `${path}.png`),
  ].filter(Boolean);
  const found = candidates.find((c) => existsSync(c));
  if (found) copyFileSync(found, target);
  else if (!existsSync(target)) throw new Error(`no icon for ${id}`);
  return `/img/items/${file}`;
}

// the spool's item model picks a texture by colour and fill: bare and full, and blue for the dyeing example
copyFileSync(join(modTextures, 'cable_spool/copper_full.png'), join(out, 'create_new_era_cable_spool.png'));
copyFileSync(join(modTextures, 'cable_spool/blue_full.png'), join(out, 'cable_spool_blue.png'));

const items = {};
function use(id) {
  if (!items[id]) {
    const [en, es] = nameOf(id);
    items[id] = { en, es, icon: icon(id), mod: id.split(':')[0] };
  }
  return id;
}

const recipes = {};
for (const file of readdirSync(recipeDir).sort()) {
  const r = JSON.parse(readFileSync(join(recipeDir, file), 'utf8'));
  const name = file.replace('.json', '');
  if (r.type === 'minecraft:crafting_shaped' || r.type === 'create:mechanical_crafting') {
    const width = Math.max(...r.pattern.map((row) => row.length));
    recipes[name] = {
      type: r.type === 'create:mechanical_crafting' ? 'mechanical' : 'shaped',
      grid: r.pattern.map((row) => [...row.padEnd(width, ' ')].map((c) => (c === ' ' ? null : use(r.key[c].item)))),
      result: use(r.result.id),
      count: r.result.count ?? 1,
      mirrored: r.accept_mirrored ?? true,
    };
  } else if (r.type === 'minecraft:crafting_shapeless') {
    recipes[name] = {
      type: 'shapeless',
      ingredients: r.ingredients.map((i) => use(i.item)),
      result: use(r.result.id),
      count: r.result.count ?? 1,
    };
  } else if (r.type === 'create:item_application') {
    recipes[name] = {
      type: 'application',
      base: use(r.ingredients[0].item),
      held: use(r.ingredients[1].item),
      result: use(r.results[0].id),
      count: r.results[0].count ?? 1,
    };
  } else if (r.type === 'create_new_era:spool_dyeing') {
    // a code recipe: spool in the middle, the same dye on its four sides (SpoolDyeRecipe)
    const dye = use('minecraft:blue_dye');
    const spool = use('create_new_era:cable_spool');
    recipes[name] = {
      type: 'shaped',
      grid: [
        [null, dye, null],
        [dye, spool, dye],
        [null, dye, null],
      ],
      result: spool,
      resultIcon: '/img/items/cable_spool_blue.png',
      count: 1,
      mirrored: true,
    };
  } else {
    throw new Error(`unknown recipe type ${r.type} in ${file}`);
  }
}

writeFileSync(join(import.meta.dirname, '..', 'lib/recipes.json'), `${JSON.stringify({ items, recipes }, null, 2)}\n`);
console.log(`${Object.keys(recipes).length} recipes, ${Object.keys(items).length} items`);
