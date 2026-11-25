// Prism Light / Dusk / Night wallpapers from the brand's boot artwork (prism_waves.svg).
// Night is the boot picture as drawn; Light and Dusk recolour it for the Light and Balanced looks.
//
// Usage (Node with Playwright, which brings its own Chromium):
//   node tools/branding/render_prism_waves.js OUT_DIR
// writes waves_night.png, waves_dusk.png, waves_light.png at 1920 x 1080. Save them as
// app/src/main/res/drawable-nodpi/wall_prism_{night,dusk,light}.jpg (JPEG quality 88, 4:4:4,
// with a faint grain so the gradients don't band on TVs).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const out = process.argv[2];
if (!out) { console.error('Usage: node render_prism_waves.js OUT_DIR'); process.exit(1); }
const base = fs.readFileSync(path.join(__dirname, 'prism_waves.svg'), 'utf8');

// Colour swaps on the master: background field, blue wave, warm wave, wave edges, the warm wave's shadow.
const looks = {
  night: [],
  dusk: [
    ['#020315', '#2c2566'], ['#080c30', '#463b8c'], ['#030318', '#2a2361'],
    ['#020c39', '#3a3a9a'], ['#073cbe', '#4f6fe0'], ['#009eff', '#5cb8ff'], ['#a8faff', '#c9fbff'],
    ['#2410a0', '#4a35b8'], ['#a000ff', '#b34dff'], ['#ff28ad', '#ff5cbe'], ['#ff9b37', '#ffad5c'], ['#270a73', '#3b2a8c'],
    ['#100b48', '#2a2370'],
  ],
  light: [
    ['#020315', '#eef1fc'], ['#080c30', '#f5f2fb'], ['#030318', '#ebe7f8'],
    ['#020c39', '#e8eeff'], ['#073cbe', '#8fb0ff'], ['#009eff', '#5cc4ff'], ['#a8faff', '#d4fbff'],
    ['#2410a0', '#e4dcff'], ['#a000ff', '#c58cff'], ['#ff28ad', '#ff86c8'], ['#ff9b37', '#ffbe7d'], ['#270a73', '#f1e8ff'],
    ['#100b48', '#ffffff'], ['#5ceaff', '#ffffff'], ['#ff9de9', '#ffffff'],
  ],
};

(async () => {
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  for (const [name, swaps] of Object.entries(looks)) {
    let svg = base;
    for (const [from, to] of swaps) svg = svg.split(from).join(to);
    await page.setContent(`<html><body style="margin:0">${svg}</body></html>`);
    await page.screenshot({ path: path.join(out, `waves_${name}.png`) });
  }
  await browser.close();
})();
