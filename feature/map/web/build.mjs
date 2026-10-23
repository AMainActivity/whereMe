import {build} from 'esbuild';
import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {dirname, relative, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const output = resolve(root, '../src/main/assets/map');
const check = process.argv.includes('--check');

async function emit(path, content) {
  if (check) {
    const existing = await readFile(path);
    if (!existing.equals(Buffer.from(content))) {
      throw new Error(`${relative(root, path)} is out of date. Run npm run build.`);
    }
  } else {
    await mkdir(dirname(path), {recursive: true});
    await writeFile(path, content);
  }
}

const result = await build({
  absWorkingDir: root,
  entryPoints: ['src/map.js'],
  outfile: resolve(output, 'map.bundle.js'),
  bundle: true,
  minify: true,
  target: 'chrome85',
  legalComments: 'eof',
  metafile: true,
  write: false,
});
for (const file of result.outputFiles) await emit(file.path, file.contents);
await emit(resolve(output, 'ol.css'), await readFile(resolve(root, 'node_modules/ol/ol.css')));
const packages = [...new Set(Object.keys(result.metafile.inputs)
  .filter(path => path.startsWith('node_modules/'))
  .map(path => {
    const [, scope, name] = path.split('/');
    return scope.startsWith('@') ? `${scope}/${name}` : scope;
  }))].sort();
const notices = [];
const config = resolve(root, 'aboutlibraries');
for (const name of packages) {
  const directory = resolve(root, 'node_modules', name);
  const pkg = JSON.parse(await readFile(resolve(directory, 'package.json'), 'utf8'));
  let license;
  for (const file of ['LICENSE', 'LICENSE.md', 'LICENSE.txt']) {
    try { license = await readFile(resolve(directory, file), 'utf8'); break; }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  if (!license) throw new Error(`Missing license: ${name}`);
  notices.push(`${name} ${pkg.version}\n${license}`);
  const fileName = name.replace(/^@/, '').replaceAll('/', '-');
  const hash = `map-${fileName}`;
  await emit(resolve(config, 'libraries', `${fileName}.json`), JSON.stringify({
    uniqueId: `npm:${name}`, artifactVersion: pkg.version,
    name: name === 'ol' ? 'OpenLayers' : name,
    description: pkg.description, website: pkg.homepage || `https://www.npmjs.com/package/${name}`,
    developers: [], licenses: [hash],
  }, null, 2) + '\n');
  // This pinned package declares ISC in package.json but ships a composite BSD/MIT notice.
  const compositeLicense = name === '@maplibre/maplibre-gl-style-spec';
  await emit(resolve(config, 'licenses', `${fileName}.json`), JSON.stringify({
    hash, name: compositeLicense ? 'MapLibre / Mapbox BSD-3-Clause and glfx.js MIT notices' : pkg.license,
    ...(compositeLicense ? {} : {spdxId: pkg.license}),
    url: compositeLicense
      ? `https://github.com/maplibre/maplibre-style-spec/blob/v${pkg.version}/LICENSE.txt`
      : `https://spdx.org/licenses/${pkg.license}.html`, content: license,
  }, null, 2) + '\n');
}
const styleLicense = (await Promise.all(['LICENSE.md', 'POSITRON_LICENSE.md']
  .map(file => readFile(resolve(root, 'src/styles', file), 'utf8')))).join('\n\n---\n\n');
notices.push(`OpenFreeMap styles and sprites (snapshot 2026-10-22)\n${styleLicense}`);
await emit(resolve(config, 'libraries/openfreemap-styles.json'), JSON.stringify({
  uniqueId: 'openfreemap-styles', artifactVersion: '2026-10-22',
  name: 'OpenFreeMap styles and sprites',
  description: 'Positron and Dark, adapted to system fonts and improved night contrast; icons from OpenFreeMap.',
  website: 'https://github.com/hyperknot/openfreemap-styles',
  developers: [], licenses: ['map-openfreemap-styles'],
}, null, 2) + '\n');
await emit(resolve(config, 'licenses/openfreemap-styles.json'), JSON.stringify({
  hash: 'map-openfreemap-styles', name: 'OpenFreeMap styles and assets licenses',
  url: 'https://github.com/hyperknot/openfreemap-styles/blob/main/LICENSE.md', content: styleLicense,
}, null, 2) + '\n');
await emit(resolve(output, 'THIRD_PARTY_NOTICES.txt'), notices.join('\n\n---\n\n'));
