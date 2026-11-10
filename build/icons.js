// Renders the launcher icons (legacy + adaptive layers) to PNG with the pre-installed Chromium.
const { chromium } = require('playwright');
const path = require('path');
const out = process.argv[2];

const mark = (s) => `
  <g transform="translate(${s.x} ${s.y}) scale(${s.k})">
    <rect x="22" y="14" width="56" height="72" rx="10" fill="#ffffff"/>
    <rect x="36" y="8" width="28" height="14" rx="6" fill="#dfe6ff" stroke="#ffffff" stroke-width="3"/>
    <path d="M34 50 l10 10 l22 -22" fill="none" stroke="#2f4ff0" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>
    <rect x="34" y="68" width="32" height="5" rx="2.5" fill="#c9d3ff"/>
  </g>`;
const bg = `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5b7bff"/><stop offset="1" stop-color="#2d45d8"/></linearGradient></defs>`;

const icons = {
  // legacy 192px (xxxhdpi 48dp)
  'ic_launcher.png': [192, `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="192" height="192">${bg}<rect x="4" y="4" width="92" height="92" rx="22" fill="url(#g)"/>${mark({x:18,y:17,k:0.64})}</svg>`],
  // adaptive layers 432px (xxxhdpi 108dp), content inside the 66dp safe zone
  'ic_launcher_bg.png': [432, `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="432" height="432">${bg}<rect width="100" height="100" fill="url(#g)"/></svg>`],
  'ic_launcher_fg.png': [432, `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="432" height="432">${mark({x:29,y:28.5,k:0.42})}</svg>`],
};

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ deviceScaleFactor: 1 });
  for (const [name, [size, svg]] of Object.entries(icons)) {
    await p.setViewportSize({ width: size, height: size });
    await p.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
    await p.screenshot({ path: path.join(out, name), omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
  }
  await b.close();
})();
