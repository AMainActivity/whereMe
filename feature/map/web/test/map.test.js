import {after, before, test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {build} from 'esbuild';
import {chromium} from 'playwright';

const assets = resolve('../src/main/assets/map');
let server, browser, origin, harness;
before(async () => {
  const result = await build({stdin: {
    contents: "import * as engine from './src/map.js'; window.engine = engine;",
    resolveDir: process.cwd(),
  }, bundle: true, write: false, target: 'chrome85'});
  harness = result.outputFiles[0].text;
  server = createServer(async (request, response) => {
    const path = new URL(request.url, 'http://local').pathname;
    if (path.includes('..')) { response.writeHead(400).end(); return; }
    try {
      const body = await readFile(resolve(assets, `.${path}`));
      response.setHeader('Content-Type', path.endsWith('.js') ? 'text/javascript' : path.endsWith('.css') ? 'text/css' : path.endsWith('.png') ? 'image/png' : path.endsWith('.json') ? 'application/json' : 'text/html');
      response.end(body);
    } catch { response.writeHead(404).end(); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  origin = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE});
});
after(async () => { await browser?.close(); await new Promise(resolve => server?.close(resolve)); });

async function pageFor(t, inspect = true) {
  const page = await browser.newPage({viewport: {width: 400, height: 700}});
  t.after(() => page.close());
  await page.addInitScript(() => {
    window.events = [];
    window.Android = {postMessage: value => window.events.push(JSON.parse(value))};
  });
  await page.route('https://tiles.openfreemap.org/**', route => route.abort());
  if (inspect) await page.route('**/map.bundle.js', route => route.fulfill({contentType: 'text/javascript', body: harness}));
  await page.goto(`${origin}/index.html`);
  await page.waitForFunction(() => window.events.some(event => event.type === 'ready'));
  return page;
}
const point = (id, longitude = 37.6, latitude = 55.7) => ({_id: id, longitude, latitude, accuracy: 20, datestart: '22.10.2026 12:00:00'});
async function data(page, points, day = '2026-10-22') {
  await page.evaluate(value => window.whereMeMap.setData(value), {day, points});
}

test('packaged bundle opens offline, renders a marker, and reports tile failures separately', async t => {
  const page = await pageFor(t, false);
  await data(page, [point(1)]);
  await page.waitForFunction(() => document.querySelector('canvas')?.width > 0);
  await page.waitForFunction(() => window.events.some(event => event.type === 'tiles' && event.failed));
  assert.equal(await page.evaluate(() => window.events.some(event => event.type === 'failed')), false);
  assert.ok(await page.locator('#map').evaluate(element => element.clientHeight) > 0);
});

test('last point stays outside clusters and layer switches are independent and idempotent', async t => {
  const page = await pageFor(t);
  await data(page, [point(1), point(2), point(3, 37.61)]);
  await page.evaluate(() => {
    const options = {previousPointsVisible: false, trackVisible: true};
    window.whereMeMap.setOptions(options);
    window.whereMeMap.setOptions(options);
  });
  assert.deepEqual(await page.evaluate(() => ({
    previousVisible: engine.previousLayer.getVisible(), trackVisible: engine.trackLayer.getVisible(),
    latest: engine.latest.getFeatures().map(f => f.getId()), previous: engine.previous.getFeatures().map(f => f.getId()),
  })), {previousVisible: false, trackVisible: true, latest: ['3'], previous: ['1', '2']});
  await page.evaluate(() => window.whereMeMap.setOptions({previousPointsVisible: false, trackVisible: false}));
  assert.equal(await page.evaluate(() => engine.latest.getFeatures().length), 1);
});

test('updating history promotes the new last point without moving the camera', async t => {
  const page = await pageFor(t);
  await data(page, [point(1), point(2, 38)]);
  await page.evaluate(() => {
    window.whereMeMap.setOptions({previousPointsVisible: false, trackVisible: false});
    engine.map.getView().setCenter([123, 456]); engine.map.getView().setZoom(8);
  });
  await data(page, [point(1), point(2, 38), point(3, 39)]);
  assert.deepEqual(await page.evaluate(() => ({center: engine.map.getView().getCenter(), zoom: engine.map.getView().getZoom(),
    latest: engine.latest.getFeatures().map(f => f.getId()), previous: engine.previous.getFeatures().map(f => f.getId())})),
  {center: [123, 456], zoom: 8, latest: ['3'], previous: ['1', '2']});
});

test('fit accounts for the latest pin and label above its geographic anchor', async t => {
  const page = await pageFor(t);
  await data(page, [point(1, 37.6, 55.0), point(2, 37.6, 56.0)]);
  const pixel = await page.evaluate(() => {
    engine.map.renderSync();
    return engine.map.getPixelFromCoordinate(engine.latest.getFeatures()[0].getGeometry().getCoordinates());
  });
  assert.ok(pixel[1] >= 60, `Latest pin and label are clipped: anchor y=${pixel[1]}`);
});

test('hiding previous points clears their accuracy but preserves selection of the last point', async t => {
  const page = await pageFor(t);
  await data(page, [point(1), point(2, 38)]);
  await page.evaluate(() => { whereMeMap.select('1'); whereMeMap.setOptions({previousPointsVisible: false, trackVisible: true}); });
  assert.equal(await page.evaluate(() => engine.selection.getFeatures().length), 0);
  await page.evaluate(() => { whereMeMap.select('2'); whereMeMap.setOptions({previousPointsVisible: false, trackVisible: false}); });
  assert.equal(await page.evaluate(() => engine.selection.getFeatures().length), 1);
});

test('coincident cluster opens a choice of previous points without counting the last point', async t => {
  const page = await pageFor(t);
  await data(page, [point(1), point(2), point(3, 37.61)]);
  await page.evaluate(() => engine.map.renderSync());
  const pixel = await page.evaluate(() => engine.map.getPixelFromCoordinate(engine.previous.getFeatures()[0].getGeometry().getCoordinates()));
  await page.mouse.click(pixel[0], pixel[1]);
  await page.waitForFunction(() => window.events.some(event => event.type === 'selection' && event.ids.length === 2));
  assert.deepEqual(await page.evaluate(() => window.events.filter(event => event.type === 'selection').at(-1).ids.sort()), ['1', '2']);
});

test('new fixes keep an open cluster selection until its points are hidden or removed', async t => {
  const page = await pageFor(t);
  await data(page, [point(1), point(2), point(3, 37.61)]);
  const pixel = await page.evaluate(() => {
    engine.map.renderSync();
    return engine.map.getPixelFromCoordinate(engine.previous.getFeatures()[0].getGeometry().getCoordinates());
  });
  await page.mouse.click(pixel[0], pixel[1]);
  await page.waitForFunction(() => events.some(event => event.type === 'selection' && event.ids.length === 2));
  await data(page, [point(1), point(2), point(3, 37.61), point(4, 37.62)]);
  assert.deepEqual(await page.evaluate(() => events.filter(event => event.type === 'selection').at(-1).ids.sort()), ['1', '2']);
  await data(page, [point(2), point(3, 37.61), point(4, 37.62)]);
  assert.deepEqual(await page.evaluate(() => events.filter(event => event.type === 'selection').at(-1).ids), ['2']);
  assert.equal(await page.evaluate(() => engine.selection.getFeatures().length), 1);
  await page.evaluate(() => whereMeMap.setOptions({previousPointsVisible: false, trackVisible: true}));
  assert.deepEqual(await page.evaluate(() => events.filter(event => event.type === 'selection').at(-1).ids), []);
});

test('tile errors follow the visible area when panning to loaded tiles and back', async t => {
  const page = await pageFor(t);
  await data(page, [point(1)]);
  await page.waitForLoadState('networkidle');
  assert.equal(await page.evaluate(() => events.filter(event => event.type === 'tiles').at(-1)?.failed), true);
  const center = await page.evaluate(() => engine.map.getView().getCenter());
  await page.unroute('https://tiles.openfreemap.org/**');
  const emptyTile = Buffer.alloc(0); // A valid MVT with no layers.
  await page.route('https://tiles.openfreemap.org/**', route => route.fulfill({contentType: 'application/x-protobuf', body: emptyTile}));
  await page.evaluate(() => engine.map.getView().setCenter([0, 0]));
  await page.waitForLoadState('networkidle');
  assert.equal(await page.evaluate(() => events.filter(event => event.type === 'tiles').at(-1)?.failed), false);
  await page.evaluate(center => engine.map.getView().setCenter(center), center);
  await page.waitForFunction(() => events.filter(event => event.type === 'tiles').at(-1)?.failed === true);
});

test('empty history clears vectors; fit and last-point commands remain safe', async t => {
  const page = await pageFor(t);
  await data(page, [point(1)]);
  await page.evaluate(() => whereMeMap.select('1'));
  await data(page, [], '2026-10-23');
  await page.evaluate(() => { whereMeMap.fitAll(); whereMeMap.showLast(); });
  assert.deepEqual(await page.evaluate(() => [engine.previous, engine.latest, engine.track, engine.selection].map(source => source.getFeatures().length)), [0, 0, 0, 0]);
  assert.equal(await page.evaluate(() => events.some(event => event.type === 'failed')), false);
});

test('retry recovers tiles without resetting history, visibility or camera', async t => {
  const page = await pageFor(t);
  await data(page, [point(1)]);
  await page.waitForFunction(() => events.some(event => event.type === 'tiles' && event.failed));
  const before = await page.evaluate(() => engine.map.getView().getCenter());
  await page.unroute('https://tiles.openfreemap.org/**');
  const emptyTile = Buffer.alloc(0); // A valid MVT with no layers.
  let recovered = 0;
  await page.route('https://tiles.openfreemap.org/**', route => { recovered++; return route.fulfill({contentType: 'application/x-protobuf', body: emptyTile}); });
  await page.evaluate(() => { whereMeMap.setOptions({previousPointsVisible: false, trackVisible: false}); whereMeMap.retryTiles(); });
  await page.waitForFunction(() => events.filter(event => event.type === 'tiles').at(-1)?.failed === false);
  await page.waitForLoadState('networkidle');
  assert.ok(recovered > 0);
  assert.equal(await page.evaluate(() => events.filter(event => event.type === 'tiles').at(-1)?.failed), false);
  assert.deepEqual(await page.evaluate(() => engine.map.getView().getCenter()), before);
  assert.equal(await page.evaluate(() => engine.latest.getFeatures().length), 1);
  assert.equal(await page.evaluate(() => engine.trackLayer.getVisible()), false);
});

test('theme changes preserve history and update native-supplied map colors', async t => {
  const page = await pageFor(t);
  await data(page, [point(1)]);
  for (const background of ['#101820', '#ffffff']) {
    await page.evaluate(background => whereMeMap.setTheme({background, surface: background, text: '#aaaaaa', primary: '#3377cc'}), background);
    assert.equal(await page.evaluate(() => document.documentElement.style.getPropertyValue('--map-background')), background);
    assert.equal(await page.evaluate(() => engine.latest.getFeatures().length), 1);
    assert.equal(await page.locator('.ol-attribution').evaluate(element => getComputedStyle(element).backgroundColor), background === '#101820' ? 'rgb(16, 24, 32)' : 'rgb(255, 255, 255)');
  }
});

test('vector markers load offline in both themes and the latest pin remains clickable', async t => {
  const page = await pageFor(t);
  const palettes = [
    {dark: false, background: '#f5f5f5', surface: '#ffffff', text: '#202124', primary: '#1759a8'},
    {dark: true, background: '#101820', surface: '#18242f', text: '#e4edf5', primary: '#a8c7fa'},
  ];
  for (const palette of palettes) {
    await page.evaluate(theme => whereMeMap.setTheme(theme), palette);
    for (const stopped of [false, true]) {
      for (const accuracy of [20, 100, 101, 500, 501, 800]) {
        const end = stopped ? {dateend: '22.10.2026 12:10:00'} : {};
        await data(page, [{...point(1), accuracy, ...end}, {...point(2, 37.61), accuracy, ...end}]);
        const icons = await page.evaluate(async () => {
          engine.map.renderSync();
          const lastLayer = engine.map.getLayers().getArray().find(layer => layer.getSource() === engine.latest);
          const previousCluster = engine.clusters.getFeatures()[0];
          const styles = [
            engine.previousLayer.getStyleFunction()(previousCluster),
            lastLayer.getStyleFunction()(engine.latest.getFeatures()[0]),
          ];
          return Promise.all(styles.map(async style => {
            const icon = style.getImage();
            icon.load();
            const image = icon.getImage(1);
            await image.decode();
            return {width: image.naturalWidth, height: image.naturalHeight, src: icon.getSrc(), label: style.getText().getText()};
          }));
        });
        assert.deepEqual(icons.map(({width, height, label}) => ({width, height, label})), [
          {width: 28, height: 28, label: '1'}, {width: 36, height: 44, label: '2'},
        ]);
        for (const icon of icons) {
          const svg = decodeURIComponent(icon.src.split(',')[1]);
          assert.ok(svg.includes(palette.surface));
          assert.ok(svg.includes(palette.text));
          const accuracyColor = accuracy <= 100 ? '#087f5b' : accuracy <= 500 ? '#c05600' : '#c92a2a';
          assert.ok(svg.includes(`fill="${accuracyColor}"`), `Both previous and latest markers must reflect accuracy ${accuracy}`);
        }
      }
    }
    // Tap the visible head of the pin, above its geographic anchor.
    const pixel = await page.evaluate(() => {
      engine.map.renderSync();
      return engine.map.getPixelFromCoordinate(engine.latest.getFeatures()[0].getGeometry().getCoordinates());
    });
    await page.evaluate(() => { whereMeMap.select(null); window.events.length = 0; });
    await page.mouse.click(pixel[0], pixel[1] - 26);
    await page.waitForFunction(() => events.some(event => event.type === 'selection' && event.ids[0] === '2'));
  }
});

test('day and night share tiles and preserve camera, selected point and layer visibility', async t => {
  const page = await pageFor(t);
  await page.unroute('https://tiles.openfreemap.org/**');
  let requests = 0;
  await page.route('https://tiles.openfreemap.org/**', route => {
    requests++;
    return route.fulfill({contentType: 'application/x-protobuf', body: Buffer.alloc(0)});
  });
  await data(page, [point(1), point(2, 37.61)]);
  await page.evaluate(() => {
    whereMeMap.setOptions({previousPointsVisible: false, trackVisible: true});
    whereMeMap.select('2');
    whereMeMap.retryTiles();
  });
  await page.waitForLoadState('networkidle');
  const before = await page.evaluate(() => ({center: engine.map.getView().getCenter(), zoom: engine.map.getView().getZoom()}));
  const loadedRequests = requests;
  assert.ok(loadedRequests > 0);
  for (const dark of [true, false, true]) {
    await page.evaluate(dark => whereMeMap.setTheme({dark, background: '#101820', surface: '#18242f', text: '#e4edf5', primary: '#a8c7fa'}), dark);
    await page.waitForFunction(dark => {
      const layers = engine.map.getLayers().getArray().slice(0, 2);
      return layers.length === 2 && layers.filter(layer => layer.getVisible()).length === 1 &&
        layers.find(layer => layer.getVisible()).getBackground() === (dark ? 'rgb(12,12,12)' : 'rgb(242,243,240)');
    }, dark);
    await page.waitForLoadState('networkidle');
    assert.deepEqual(await page.evaluate(() => ({center: engine.map.getView().getCenter(), zoom: engine.map.getView().getZoom()})), before);
    assert.deepEqual(await page.evaluate(() => ({last: engine.latest.getFeatures()[0].getId(), selected: engine.selection.getFeatures().length,
      previous: engine.previousLayer.getVisible(), track: engine.trackLayer.getVisible()})),
    {last: '2', selected: 1, previous: false, track: true});
    assert.equal(await page.evaluate(() => {
      const layers = engine.map.getLayers().getArray().slice(0, 2);
      return layers[0].getSource() === layers[1].getSource();
    }), true);
    assert.equal(requests, loadedRequests, 'Theme switch should reuse loaded vector tiles');
  }
});

test('rapid theme changes during local style loading settle on the last requested theme offline', async t => {
  const page = await pageFor(t);
  await data(page, [point(1)]);
  await page.evaluate(() => {
    for (const dark of [true, false, true]) whereMeMap.setTheme({dark, background: '#101820', surface: '#18242f', text: '#e4edf5', primary: '#a8c7fa'});
  });
  await page.waitForFunction(() => engine.map.getLayers().getArray().slice(0, 2).some(layer => layer.getVisible() && layer.getBackground() === 'rgb(12,12,12)'));
  assert.equal(await page.evaluate(() => engine.map.getLayers().getArray().slice(0, 2).filter(layer => layer.getVisible()).length), 1);
  assert.equal(await page.evaluate(() => engine.latest.getFeatures().length), 1);
  await page.waitForFunction(() => events.filter(event => event.type === 'tiles').at(-1)?.failed === true);
  await page.unroute('https://tiles.openfreemap.org/**');
  await page.route('https://tiles.openfreemap.org/**', route => route.fulfill({contentType: 'application/x-protobuf', body: Buffer.alloc(0)}));
  await page.evaluate(() => whereMeMap.retryTiles());
  await page.waitForLoadState('networkidle');
  assert.equal(await page.evaluate(() => events.filter(event => event.type === 'tiles').at(-1)?.failed), false);
  assert.equal(await page.evaluate(() => events.some(event => event.type === 'failed')), false);
});

test('house numbers use tile attributes at close zoom in both themes', async t => {
  const page = await pageFor(t);
  await data(page, [point(1)]);
  for (const dark of [false, true]) {
    await page.evaluate(dark => whereMeMap.setTheme({dark, background: '#101820', surface: '#18242f', text: '#e4edf5', primary: '#a8c7fa'}), dark);
    await page.waitForFunction(dark => {
      const layer = engine.map.getLayers().getArray().slice(0, 2).find(layer => layer.getVisible());
      return layer?.getBackground() === (dark ? 'rgb(12,12,12)' : 'rgb(242,243,240)');
    }, dark);
    const labels = await page.evaluate(() => {
      const feature = engine.latest.getFeatures()[0].clone();
      feature.setProperties({'mvt:layer': 'housenumber', housenumber: '12А/3'});
      const layer = engine.map.getLayers().getArray().slice(0, 2).find(layer => layer.getVisible());
      const style = layer.getStyleFunction();
      // Mapbox style zoom 16 corresponds to OpenLayers view zoom 17 (512 vs 256 pixels).
      return [16, 17, 19].map(zoom => {
        const styles = style(feature, engine.map.getView().getResolutionForZoom(zoom)) || [];
        return styles.map(value => value.getText()).filter(Boolean).map(text => ({
          text: text.getText(), fill: text.getFill().getColor(),
          halo: text.getStroke().getColor(), haloWidth: text.getStroke().getWidth(),
        }));
      });
    });
    assert.deepEqual(labels[0], [], 'House numbers should not clutter the overview');
    for (const values of labels.slice(1)) {
      assert.equal(values.length, 1);
      assert.equal(values[0].text, '12А/3', 'Preserve letters, buildings and fractions from the source');
      assert.notDeepEqual(values[0].fill, values[0].halo);
      assert.ok(values[0].haloWidth > 0);
    }
  }
});

test('shops and amenities have icons and local names in both themes', async t => {
  const page = await pageFor(t);
  await data(page, [point(1)]);
  for (const dark of [false, true]) {
    await page.evaluate(dark => whereMeMap.setTheme({dark, background: '#101820', surface: '#18242f', text: '#e4edf5', primary: '#a8c7fa'}), dark);
    await page.waitForFunction(dark => {
      const layer = engine.map.getLayers().getArray().slice(0, 2).find(layer => layer.getVisible());
      return layer?.getBackground() === (dark ? 'rgb(12,12,12)' : 'rgb(242,243,240)');
    }, dark);
    await page.waitForLoadState('networkidle');
    const result = await page.evaluate(() => {
      const layer = engine.map.getLayers().getArray().slice(0, 2).find(layer => layer.getVisible());
      const feature = engine.latest.getFeatures()[0].clone();
      const sample = (zoom, category, names) => {
        feature.setProperties({'mvt:layer': 'poi', class: category, rank: 1, name: '', 'name:ru': undefined, ...names});
        const styles = layer.getStyleFunction()(feature, engine.map.getView().getResolutionForZoom(zoom)) || [];
        return {icons: styles.filter(s => s.getImage()).length,
          labels: styles.map(s => s.getText()).filter(Boolean).map(text => text.getText())};
      };
      return {
        overview: sample(14, 'shop', {name: 'Магазин'}),
        close: ['shop', 'grocery', 'cafe', 'pharmacy', 'unknown-category'].map(category => sample(17, category, {name: 'Local name', 'name:ru': 'Название'})),
        unnamed: sample(17, 'shop', {}),
        local: sample(17, 'cafe', {name: 'Кофейня'}),
      };
    });
    assert.equal(result.overview.icons, 0);
    assert.deepEqual(result.overview.labels, []);
    for (const item of result.close) {
      assert.ok(item.icons > 0, `Known categories and other POIs must have a symbol: ${JSON.stringify(result)}`);
      assert.ok(item.labels.includes('Название'));
    }
    assert.ok(result.unnamed.icons > 0, 'Places without a name still have an icon');
    assert.ok(result.local.labels.includes('Кофейня'));
  }
});
