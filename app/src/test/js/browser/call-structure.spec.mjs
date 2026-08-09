import { test, expect } from '@playwright/test';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const pageAt = (name) => pathToFileURL(resolve(`build/viewer-test/${name}.html`)).href;

const open = (name) => async ({ page }) => {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(pageAt(name));
  await page.waitForFunction(() => typeof window.cy?.nodes === 'function');
  expect(errors, 'the page threw while loading').toEqual([]);
};

const leafLabels = (page) => page.evaluate(() => window.cy.nodes()
  .filter((n) => n.data('type') !== 'METHOD' && n.visible())
  .map((n) => n.data('label')).sort());

const boxLabels = (page) => page.evaluate(() => window.cy.nodes('[type = "METHOD"]')
  .filter((n) => n.visible())
  .map((n) => n.data('label')).sort());

const tapBox = (page, label) => page.evaluate((l) => window.cy.nodes('[type = "METHOD"]')
  .filter((n) => n.data('label') === l).emit('tap'), label);

const tapLeaf = (page, label) => page.evaluate((l) => window.cy.nodes()
  .filter((n) => n.data('type') !== 'METHOD' && n.data('label') === l).emit('tap'), label);

/**
 * The fixture where following dataflow finds nothing.
 *
 * `main` does `app.func1()` - no argument, no result - so not one edge crosses from `main` into
 * `func1`. Reveal follows edges, so before call structure was navigable this page opened on four
 * nodes and clicking every one of them changed nothing: 19 of the fixture's 23 leaves could not be
 * put on screen by any sequence of clicks.
 */
test.describe('member: a callee no edge reaches', () => {
  test.beforeEach(open('member'));

  // A closed callee has to be *drawn* to be clickable, and Cytoscape will not draw a compound node
  // with no visible children whatever display it is given - so the box exists on the page only
  // because its RETURN node is showing inside it.
  test('a callee of the entry method is drawn as its own name', async ({ page }) => {
    expect(await boxLabels(page)).toEqual(['func1', 'main']);
    expect(await leafLabels(page)).toEqual(['App', 'app', 'args', 'func1', 'main']);
  });

  // Depth, not breadth: getMemberX is called from func1, and offering its stub too would put the
  // whole call tree on screen at open. The positive half is what stops this passing on a page that
  // draws no stub at all.
  test('a callee of a closed callee is not drawn', async ({ page }) => {
    const boxes = await boxLabels(page);
    expect(boxes).toContain('func1');
    expect(boxes).not.toContain('getMemberX');
  });

  test('clicking a closed box opens it to its own body', async ({ page }) => {
    expect(await leafLabels(page)).not.toContain('memberA');

    await tapBox(page, 'func1');

    const after = await leafLabels(page);
    // func1's own locals, none of which any edge could have reached from main.
    for (const label of ['a', 'b', 'memberA', 'y', 'y1', 'c', 'd', 'j']) {
      expect(after, `${label} should be on screen after opening func1`).toContain(label);
    }
    // And now that func1 is open, the method *it* calls is offered in turn.
    expect(await boxLabels(page)).toEqual(['func1', 'getMemberX', 'main']);
  });

  test('folding an open box leaves it drawn as a stub, not gone', async ({ page }) => {
    await tapBox(page, 'func1');
    expect(await leafLabels(page)).toContain('memberA');

    await tapBox(page, 'func1');
    expect(await leafLabels(page)).not.toContain('memberA');
    // The handle survives the fold. Without this the box would vanish and the only way back would be
    // a reload, which is the dead end this whole mechanism exists to remove.
    expect(await boxLabels(page)).toEqual(['func1', 'main']);
  });

  // The end-to-end half of the unit test: the node the reader can see is the node that opens the
  // method. `func1` matches exactly one leaf - the box of the same name is filtered out by type -
  // and the count before is what makes this fail if the click does nothing.
  test('clicking the name of a closed method opens its body', async ({ page }) => {
    expect(await leafLabels(page)).toEqual(['App', 'app', 'args', 'func1', 'main']);
    await tapLeaf(page, 'func1');
    const opened = await leafLabels(page);
    expect(opened).toHaveLength(23);
    expect(opened).toContain('memberX');
  });
});

/**
 * `funcCall` is where a callee has a callee with a body worth hiding: methodB holds two methodC
 * call sites, each of nine nodes. `member`'s nesting cannot ask this question - getMemberX's whole
 * body is its RETURN node, so a stub and an open box look identical there.
 */
test.describe('funcCall: one level per click', () => {
  test.beforeEach(open('funcCall'));

  test('opening a box shows its own leaves and only the names of the boxes inside it', async ({ page }) => {
    await tapBox(page, 'methodB');

    const leaves = await leafLabels(page);
    // methodB's own body, which is what was asked for.
    for (const own of ['11', 'd', '13', 'f']) {
      expect(leaves, `${own} is methodB's own and should be on screen`).toContain(own);
    }
    // One stub per call site, and nothing else from either. `methodC` appears twice because the
    // callee is inlined per call site; every other name below belongs to a body still folded away.
    expect(leaves.filter((l) => l === 'methodC')).toEqual(['methodC', 'methodC']);
    for (const inside of ['paramH', 'div', 'g', 'X1', 'X2', 'memberX']) {
      expect(leaves, `${inside} belongs to methodC and should still be hidden`).not.toContain(inside);
    }
  });
});
