import MVT from 'ol/format/MVT.js';
import VectorTileLayer from 'ol/layer/VectorTile.js';
import VectorTileSource from 'ol/source/VectorTile.js';
import {applyStyle} from 'ol-mapbox-style';
import positron from './styles/positron.json';
import dark from './styles/dark.json';

export function createBasemap(map, report) {
  // OpenFreeMap publishes data through zoom 14; OpenLayers overzooms it beyond that.
  const source = new VectorTileSource({
    format: new MVT({layerName: 'mvt:layer'}),
    url: 'https://tiles.openfreemap.org/planet/latest/{z}/{x}/{y}.pbf',
    maxZoom: 14,
    attributions: '<a href="https://openfreemap.org/">OpenFreeMap</a> · ' +
      '<a href="https://openmaptiles.org/">© OpenMapTiles</a> · ' +
      '<a href="https://www.openstreetmap.org/copyright">© OpenStreetMap</a>',
  });
  const styles = [positron, dark].map(style => ({
    style, ready: false, loading: false, failed: false,
    layer: new VectorTileLayer({
      source, visible: false, declutter: true,
      background: style.layers.find(layer => layer.type === 'background').paint['background-color'],
    }),
  }));
  for (const entry of styles) map.getLayers().insertAt(0, entry.layer);
  let active = styles[0];
  const failedTiles = new Set();

  function reportTiles() {
    const view = map.getView();
    const grid = source.getTileGrid();
    const zoom = grid.getZForResolution(view.getResolution(), source.zDirection);
    const visible = grid.getTileRangeForExtentAndZ(view.calculateExtent(map.getSize()), zoom);
    const failed = active.failed || [...failedTiles].some(key => {
      const coordinate = key.split('/').map(Number);
      return coordinate[0] === zoom && visible.contains(coordinate);
    });
    report({type: 'tiles', failed});
  }
  function updateVisibility() {
    for (const entry of styles) entry.layer.setVisible(entry === active && entry.ready);
    reportTiles();
  }
  async function prepare(entry) {
    if (entry.ready || entry.loading) return;
    entry.loading = true;
    entry.failed = false;
    try {
      await applyStyle(entry.layer, entry.style, {
        source: 'openmaptiles', updateSource: false, styleUrl: location.href,
      });
      entry.ready = true;
    } catch {
      // Local history remains usable even if the basemap cannot be prepared.
      entry.failed = true;
    } finally {
      entry.loading = false;
      updateVisibility();
    }
  }
  source.on('tileloaderror', event => { failedTiles.add(event.tile.getTileCoord().join('/')); reportTiles(); });
  source.on('tileloadend', event => { failedTiles.delete(event.tile.getTileCoord().join('/')); reportTiles(); });
  map.on('moveend', reportTiles);

  return {
    setTheme(isDark) {
      active = styles[isDark ? 1 : 0];
      updateVisibility();
      void prepare(active);
    },
    retry() {
      failedTiles.clear();
      source.refresh();
      void prepare(active);
      reportTiles();
    },
  };
}
