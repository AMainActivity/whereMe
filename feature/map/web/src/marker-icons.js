import Icon from 'ol/style/Icon.js';

export const LATEST_MARKER_HEIGHT = 44;
export const PREVIOUS_MARKER_SIZE = 28;

// The surface/text outline follows the app theme; white symbols contrast with
// all three accuracy colors independently of the theme or underlying tiles.
const ACCURACY_COLORS = {good: '#087f5b', fair: '#c05600', poor: '#c92a2a'};

export function createMarkerIcon(theme, isLast, stopped, accuracy) {
  const size = isLast ? '36 44' : '28 28';
  const center = isLast ? 18 : 14;
  const symbolColor = '#ffffff';
  const body = isLast
    ? `<path d="M18 43C15 37 2 28 2 18a16 16 0 0 1 32 0c0 10-13 19-16 25Z"
        fill="${theme.surface}" stroke="${theme.text}" stroke-width="1.5"/>
       <circle cx="18" cy="18" r="11" fill="${ACCURACY_COLORS[accuracy]}"/>`
    : `<circle cx="14" cy="14" r="12" fill="${theme.surface}" stroke="${theme.text}" stroke-width="2"/>
       <circle cx="14" cy="14" r="9" fill="${ACCURACY_COLORS[accuracy]}"/>`;
  const symbol = stopped
    ? `<path d="M${center - 5} ${center - 5}h3v10h-3zM${center + 2} ${center - 5}h3v10h-3z" fill="${symbolColor}"/>`
    : `<circle cx="${center}" cy="${center}" r="4" fill="${symbolColor}"/>`;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${isLast ? 36 : PREVIOUS_MARKER_SIZE}"
    height="${isLast ? LATEST_MARKER_HEIGHT : PREVIOUS_MARKER_SIZE}" viewBox="0 0 ${size}">${body}${symbol}</svg>`;
  return new Icon({src: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`, anchor: [0.5, isLast ? 1 : 0.5]});
}
