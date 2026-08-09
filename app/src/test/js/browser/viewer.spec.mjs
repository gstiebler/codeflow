import { test, expect } from '@playwright/test';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PAGE = pathToFileURL(resolve('build/viewer-test/funcCall.html')).href;

test.beforeEach(async ({ page }) => {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(PAGE);
  // Checking the type, not just presence: `!== undefined` would be satisfied by anything at all,
  // and this guard is the only thing standing between a page that never initialised and a green run.
  await page.waitForFunction(() => typeof window.cy?.nodes === 'function');
  expect(errors, 'the page threw while loading').toEqual([]);
});

test('draws the canvas at a usable size', async ({ page }) => {
  const box = await page.locator('#graph canvas').first().boundingBox();
  expect(box.width).toBeGreaterThan(100);
  expect(box.height).toBeGreaterThan(100);
});

const leafLabels = (page) => page.evaluate(() => window.cy.nodes()
  .filter((n) => n.data('type') !== 'METHOD' && n.visible())
  .map((n) => n.data('label')).sort());

const boxLabels = (page) => page.evaluate(() => window.cy.nodes('[type = "METHOD"]')
  .filter((n) => n.visible())
  .map((n) => n.data('label')).sort());

const tapLeaf = (page, label) => page.evaluate((l) => window.cy.nodes()
  .filter((n) => n.data('type') !== 'METHOD' && n.data('label') === l).emit('tap'), label);

const tapBox = (page, label) => page.evaluate((l) => window.cy.nodes('[type = "METHOD"]')
  .filter((n) => n.data('label') === l).emit('tap'), label);

// `App` is the opaque node for `new App()`: the class writes no constructor, so there is no body
// to inline and the object is a value from outside. It is a leaf child of `main`, so it opens with
// the rest of the entry method's own nodes.
//
// `methodA` and `methodB` are not bodies: each is the one RETURN node a closed callee is drawn as,
// which is what makes the box exist on the page and therefore clickable.
const OPENING = ['5', '8', 'App', 'app', 'args', 'e', 'main', 'methodA', 'methodB', 'x', 'y'];

// Hiding, not removing. Under the old folding this read 11 - which is exactly the trap that made
// a node count comparable to the payload only after expanding everything first.
test('holds the whole payload however little is displayed', async ({ page }) => {
  const { nodes, edges } = await page.evaluate(() => ({
    nodes: window.cy.nodes().length,
    edges: window.cy.edges().length,
  }));
  // Three more than before `new X(...)` became a value: one opaque constructor node per `new` of a
  // class that declares none, each with an edge into the variable it is assigned to.
  expect(nodes).toBe(42);
  expect(edges).toBe(29);
});

test('opens showing the entry method body and each callee as a closed box', async ({ page }) => {
  expect(await leafLabels(page)).toEqual(OPENING);
  // The two methods main calls, and no deeper: methodB's own callees stay out until it is opened.
  expect(await boxLabels(page)).toEqual(['main', 'methodA', 'methodB']);
  expect(await boxLabels(page)).not.toContain('methodC');
});

test('clicking a node reveals its neighbourhood three hops out', async ({ page }) => {
  await tapLeaf(page, 'x');
  const leaves = await leafLabels(page);
  // a, + , b and c are 1..3 hops from x and were all hidden a moment ago.
  expect(leaves).toContain('a');
  expect(leaves).toContain('+');
  expect(leaves).toContain('b');
  expect(leaves).toContain('c');
  // methodA's return node is exactly 4 hops out. This is what fails if the bound is off by one,
  // and it only means anything next to the four assertions above. It still bites now that a closed
  // callee is drawn as that same node: the walk has opened methodA, so its stub is gone, and the
  // label can only come back by the RETURN itself being revealed.
  expect(leaves).not.toContain('methodA');
  // methodA's box was already there, drawn as its stub; what the click changed is that it now holds
  // a body. methodB is still shut.
  expect(await boxLabels(page)).toEqual(['main', 'methodA', 'methodB']);
});

test('reveals accumulate across clicks', async ({ page }) => {
  await tapLeaf(page, 'x');
  const afterX = await leafLabels(page);
  await tapLeaf(page, 'e');
  const afterE = await leafLabels(page);

  // Nothing the first click revealed may vanish on the second.
  for (const label of afterX) expect(afterE).toContain(label);
  // And the second click has to actually add something, or the loop above proves nothing.
  expect(afterE.length).toBeGreaterThan(afterX.length);
  expect(afterE).toContain('d');
});

// The state the "never set display on a METHOD node" rule exists for. X1 flows only into X2, both
// of them inside the methodC boxes nested in methodB, so methodB is drawn purely on the strength of
// grandchildren. A box whose display came from its own children would go dark here and take the
// revealed grandchildren with it.
test('draws a box whose only revealed nodes are grandchildren', async ({ page }) => {
  await tapLeaf(page, 'X1');

  // Both methodC call sites: the label appears twice, so the tap fans out to both.
  expect(await boxLabels(page)).toEqual(['main', 'methodA', 'methodB', 'methodC', 'methodC']);

  const leaves = await leafLabels(page);
  expect(leaves).toContain('X1');
  expect(leaves).toContain('X2');
  // Every one of methodB's own children is still hidden - without these the assertion above would
  // hold on a page that reveals far more than it was asked to. `methodB` is in that list twice
  // over: it is the box's RETURN node, and it is also the stub the box would be drawn as were it
  // shut, which a box open through grandchildren alone must not have.
  for (const own of ['methodB', 'd', '11', 'f', '13']) expect(leaves).not.toContain(own);
});

test('folding a method box hides its contents, nested boxes and all', async ({ page }) => {
  await tapLeaf(page, 'e');
  // methodB is open, so both of the methodC call sites it holds are offered in turn.
  expect(await boxLabels(page)).toEqual(['main', 'methodA', 'methodB', 'methodC', 'methodC']);

  await tapBox(page, 'methodB');
  // Both methodC boxes go. If the fold used children() instead of descendants(), whichever one `e`
  // reached would keep its revealed node and survive.
  expect(await boxLabels(page)).toEqual(['main', 'methodA', 'methodB']);
  expect(await leafLabels(page)).toEqual(OPENING);
});

// The computed label rather than data('badge'): the badge being right in the data and the
// stylesheet still pointing at the plain name is a page that draws none of this, and reading the
// data alone would call it green.
const drawnLabel = (page, label) => page.evaluate((l) => window.cy.nodes()
  .filter((n) => n.data('label') === l).map((n) => n.style('label')), label);

// Without this a value whose story continues off screen is drawn exactly like one that ends here.
// `x` is passed to methodA, whose body is closed at open, so it has one hidden edge out; `5` flows
// only into `x`, which is on screen, so it has nothing to declare - and that pairing is what fails
// if every node is annotated regardless.
test('a node with hidden neighbours says how many, and one without says nothing', async ({ page }) => {
  expect(await drawnLabel(page, 'x')).toEqual(['x ↓1']);
  expect(await drawnLabel(page, '5')).toEqual(['5']);
});

// The half that fails if the badge is computed once at load and never recomputed. Clicking `x`
// reveals everything within three hops, so nothing adjacent to it is missing any more and the
// annotation has to go away on its own.
test('revealing what a node reaches clears its badge', async ({ page }) => {
  expect(await drawnLabel(page, 'x')).toEqual(['x ↓1']);
  await tapLeaf(page, 'x');
  expect(await drawnLabel(page, 'x')).toEqual(['x']);
});

test('R returns to the opening set', async ({ page }) => {
  await tapLeaf(page, 'x');
  await tapLeaf(page, 'e');
  // Without this, the reset below would pass on a page where clicking never revealed anything.
  expect((await leafLabels(page)).length).toBeGreaterThan(OPENING.length);

  await page.keyboard.press('r');
  expect(await leafLabels(page)).toEqual(OPENING);
});
