import {createBasemap} from './basemap.js';
import Map from 'ol/Map.js';
import View from 'ol/View.js';
import Feature from 'ol/Feature.js';
import Point from 'ol/geom/Point.js';
import LineString from 'ol/geom/LineString.js';
import {circular} from 'ol/geom/Polygon.js';
import {fromLonLat} from 'ol/proj.js';
import {createEmpty, extend, isEmpty} from 'ol/extent.js';
import VectorLayer from 'ol/layer/Vector.js';
import VectorSource from 'ol/source/Vector.js';
import Cluster from 'ol/source/Cluster.js';
import {defaults as controls} from 'ol/control/defaults.js';
import {Circle as CircleStyle, Fill, Stroke, Style, Text} from 'ol/style.js';
import {createMarkerIcon, LATEST_MARKER_HEIGHT, PREVIOUS_MARKER_SIZE} from './marker-icons.js';

const POINT_ZOOM = 16;
const CLUSTER_DISTANCE = 40;
const MARKER_LABEL_GAP = 8;
const CAMERA_PADDING = [LATEST_MARKER_HEIGHT + MARKER_LABEL_GAP + 16, 80, 48, 24];
const ACCURACY_GOOD = 100;
const ACCURACY_FAIR = 500;
const report = window.reportMapEvent;
const previous = new VectorSource();
const latest = new VectorSource();
const track = new VectorSource();
const selection = new VectorSource();
const position = new VectorSource();
const clusters = new Cluster({source: previous, distance: CLUSTER_DISTANCE});
let theme = {dark: false, background: '#f5f5f5', surface: '#ffffff', text: '#202124', primary: '#1759a8'};
let markerIcons = {};
let points = [];
let day;
let selectedIds = [];
let options = {previousPointsVisible: true, trackVisible: true};
function markerStyle(feature, isLast) {
  const point = feature.get('point');
  const color = point.accuracy <= ACCURACY_GOOD ? 'good' : point.accuracy <= ACCURACY_FAIR ? 'fair' : 'poor';
  const stopped = Boolean(point.dateend);
  const key = `${isLast}-${color}-${stopped}`;
  const icon = markerIcons[key] ??= createMarkerIcon(theme, isLast, stopped, color);
  return new Style({
    image: icon,
    text: new Text({
      text: String(feature.get('number')),
      offsetY: -(isLast ? LATEST_MARKER_HEIGHT : PREVIOUS_MARKER_SIZE / 2) - MARKER_LABEL_GAP,
      fill: new Fill({color: theme.text}), stroke: new Stroke({color: theme.surface, width: 3}),
    }),
  });
}
const previousLayer = new VectorLayer({source: clusters, style: feature => {
  const members = feature.get('features');
  if (members.length === 1) return markerStyle(members[0], false);
  return new Style({
    image: new CircleStyle({radius: 18, fill: new Fill({color: theme.surface}), stroke: new Stroke({color: theme.primary, width: 3})}),
    text: new Text({text: String(members.length), fill: new Fill({color: theme.text})}),
  });
}});
const latestLayer = new VectorLayer({source: latest, style: feature => markerStyle(feature, true)});
const trackLayer = new VectorLayer({source: track});
const selectionLayer = new VectorLayer({source: selection});
const positionLayer = new VectorLayer({source: position});
const map = new Map({
  target: 'map',
  controls: controls({zoom: false, rotate: false, attributionOptions: {collapsible: false}}),
  layers: [trackLayer, previousLayer, selectionLayer, latestLayer, positionLayer],
  view: new View({center: fromLonLat([0, 0]), zoom: 2, maxZoom: 19, enableRotation: false}),
});
const basemap = createBasemap(map, report);

function applyTheme() {
  basemap.setTheme(theme.dark);
  markerIcons = {};
  document.documentElement.style.setProperty('--map-background', theme.background);
  document.documentElement.style.setProperty('--map-surface', theme.surface);
  document.documentElement.style.setProperty('--map-text', theme.text);
  trackLayer.setStyle(new Style({stroke: new Stroke({color: theme.primary, width: 3})}));
  selectionLayer.setStyle(new Style({stroke: new Stroke({color: theme.primary, width: 2}), fill: new Fill({color: `${theme.primary}26`})}));
  positionLayer.setStyle(new Style({
    image: new CircleStyle({radius: 7, fill: new Fill({color: theme.primary}), stroke: new Stroke({color: theme.surface, width: 3})}),
    stroke: new Stroke({color: theme.primary, width: 1}), fill: new Fill({color: `${theme.primary}20`}),
  }));
  previousLayer.changed();
  latestLayer.changed();
}

function fit(features) {
  const extent = createEmpty();
  for (const feature of features) extend(extent, feature.getGeometry().getExtent());
  if (!isEmpty(extent)) map.getView().fit(extent, {padding: CAMERA_PADDING, maxZoom: POINT_ZOOM});
}
function fitAll() {
  if (options.previousPointsVisible || options.trackVisible) fit([...previous.getFeatures(), ...latest.getFeatures()]);
  else fit(latest.getFeatures());
}
function accuracyFeature(point) {
  const geometry = circular([point.longitude, point.latitude], point.accuracy);
  geometry.transform('EPSG:4326', 'EPSG:3857');
  return new Feature(geometry);
}
function setSelection(ids) {
  const changed = ids.length !== selectedIds.length || ids.some((id, index) => id !== selectedIds[index]);
  selectedIds = ids;
  selection.clear();
  const point = ids.length === 1 ? points.find(value => String(value._id) === ids[0]) : null;
  if (point && point.accuracy > 0) selection.addFeature(accuracyFeature(point));
  if (changed) report({type: 'selection', ids});
}
function select(id) {
  setSelection(id === null ? [] : [id]);
}
function setOptions(value) {
  options = value;
  previousLayer.setVisible(value.previousPointsVisible);
  trackLayer.setVisible(value.trackVisible);
  if (!value.previousPointsVisible) setSelection(selectedIds.filter(id => id === String(points[points.length - 1]?._id)));
}
function setData(value) {
  const changedDay = day !== value.day;
  const wasEmpty = points.length === 0;
  day = value.day;
  points = value.points;
  previous.clear(); latest.clear(); track.clear();
  const features = points.map((point, index) => {
    const feature = new Feature({geometry: new Point(fromLonLat([point.longitude, point.latitude])), point, number: index + 1});
    feature.setId(String(point._id));
    return feature;
  });
  if (features.length) {
    previous.addFeatures(features.slice(0, -1));
    latest.addFeature(features[features.length - 1]);
    if (features.length > 1) track.addFeature(new Feature(new LineString(features.map(feature => feature.getGeometry().getCoordinates()))));
  }
  const ids = new Set(points.map(point => String(point._id)));
  setSelection(changedDay ? [] : selectedIds.filter(id => ids.has(id)));
  setOptions(options);
  if (changedDay || wasEmpty) fitAll();
}

map.on('singleclick', event => {
  const hit = map.forEachFeatureAtPixel(event.pixel, feature => feature, {
    layerFilter: layer => layer === previousLayer || layer === latestLayer,
    hitTolerance: 8,
  });
  if (!hit) { setSelection([]); return; }
  const members = hit.get('features') || [hit];
  if (members.length > 1) {
    const extent = createEmpty();
    members.forEach(feature => extend(extent, feature.getGeometry().getExtent()));
    const coincident = extent[0] === extent[2] && extent[1] === extent[3];
    if (!coincident && map.getView().getZoom() < POINT_ZOOM) { fit(members); return; }
  }
  setSelection(members.map(feature => String(feature.getId())));
});

function retryTiles() {
  basemap.retry();
}
window.addEventListener('online', retryTiles);
window.whereMeMap = {
  setData, setOptions, select, fitAll, retryTiles,
  showLast() { fit(latest.getFeatures()); },
  setTheme(value) { theme = value; applyTheme(); },
  showLocation(point) {
    position.clear();
    const feature = new Feature(new Point(fromLonLat([point.longitude, point.latitude])));
    position.addFeature(feature);
    if (point.accuracy > 0) position.addFeature(accuracyFeature(point));
    fit(position.getFeatures());
  },
};
applyTheme();
report({type: 'ready'});

// ES module exports allow engine tests to exercise the real layers and projection in Chromium.
export {map, previous, latest, track, clusters, selection, previousLayer, trackLayer};
