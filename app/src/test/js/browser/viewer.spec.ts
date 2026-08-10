import { test, expect, type Page } from '@playwright/test';
import { RENDERERS, cytoscape, captionsOf, labels, type Probe } from './probes.ts';

// `App` is the opaque node for `new App()`: the class writes no constructor, so there is no body
// to inline and the object is a value from outside. It is a leaf child of `main`, so it opens with
// the rest of the entry method's own nodes.
//
// `methodA` and `methodB` are not bodies: each is the one RETURN node a closed callee is drawn as,
// which is what makes the box exist on the page and therefore clickable.
const OPENING = ['5', '8', 'App', 'app', 'args', 'e', 'main', 'methodA', 'methodB', 'x', 'y'];

const open = (probe: Probe) => async ({ page }: { page: Page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(probe.page('funcCall'));
  await probe.settled(page);
  expect(errors, 'the page threw while loading').toEqual([]);
};

/**
 * One suite, both pages.
 *
 * Every question below is about what a reader sees, and neither renderer is the authority on that:
 * Cytoscape works an edge's and a box's visibility out for itself where React Flow is told both, so
 * an answer that differs between them is model.ts being wrong about one of the two.
 */
for (const probe of RENDERERS) {
  test.describe(`funcCall on ${probe.name}`, () => {
    test.beforeEach(open(probe));

    test('draws the graph at a usable size', async ({ page }) => {
      const box = await page.locator(probe.surface).first().boundingBox();
      expect(box!.width).toBeGreaterThan(100);
      expect(box!.height).toBeGreaterThan(100);
    });

    test('opens showing the entry method body and each callee as a closed box', async ({ page }) => {
      expect(await labels(probe.leafCaptions(page))).toEqual(OPENING);
      // The two methods main calls, and no deeper: methodB's own callees stay out until it is opened.
      expect(await labels(probe.boxLabels(page))).toEqual(['main', 'methodA', 'methodB']);
      expect(await labels(probe.boxLabels(page))).not.toContain('methodC');
    });

    test('clicking a node reveals its neighbourhood three hops out', async ({ page }) => {
      await probe.tapLeaf(page, 'x');
      const leaves = await labels(probe.leafCaptions(page));
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
      expect(await labels(probe.boxLabels(page))).toEqual(['main', 'methodA', 'methodB']);
    });

    test('reveals accumulate across clicks', async ({ page }) => {
      await probe.tapLeaf(page, 'x');
      const afterX = await labels(probe.leafCaptions(page));
      await probe.tapLeaf(page, 'e');
      const afterE = await labels(probe.leafCaptions(page));

      // Nothing the first click revealed may vanish on the second.
      for (const label of afterX) expect(afterE).toContain(label);
      // And the second click has to actually add something, or the loop above proves nothing.
      expect(afterE.length).toBeGreaterThan(afterX.length);
      expect(afterE).toContain('d');
    });

    test('folding a method box hides its contents, nested boxes and all', async ({ page }) => {
      await probe.tapLeaf(page, 'e');
      // methodB is open, so both of the methodC call sites it holds are offered in turn.
      expect(await labels(probe.boxLabels(page))).toEqual(['main', 'methodA', 'methodB', 'methodC', 'methodC']);

      await probe.tapBox(page, 'methodB');
      // Both methodC boxes go. If the fold used children rather than descendants, whichever one `e`
      // reached would keep its revealed node and survive.
      expect(await labels(probe.boxLabels(page))).toEqual(['main', 'methodA', 'methodB']);
      expect(await labels(probe.leafCaptions(page))).toEqual(OPENING);
    });

    // Without this a value whose story continues off screen is drawn exactly like one that ends here.
    // `x` is passed to methodA, whose body is closed at open, so it has one hidden edge out; `5` flows
    // only into `x`, which is on screen, so it has nothing to declare - and that pairing is what fails
    // if every node is annotated regardless.
    test('a node with hidden neighbours says how many, and one without says nothing', async ({ page }) => {
      expect(await captionsOf(probe.leafCaptions(page), 'x')).toEqual(['x ↓1']);
      expect(await captionsOf(probe.leafCaptions(page), '5')).toEqual(['5']);
    });

    // The half that fails if the badge is computed once at load and never recomputed. Clicking `x`
    // reveals everything within three hops, so nothing adjacent to it is missing any more and the
    // annotation has to go away on its own.
    test('revealing what a node reaches clears its badge', async ({ page }) => {
      expect(await captionsOf(probe.leafCaptions(page), 'x')).toEqual(['x ↓1']);
      await probe.tapLeaf(page, 'x');
      expect(await captionsOf(probe.leafCaptions(page), 'x')).toEqual(['x']);
    });

    test('R returns to the opening set', async ({ page }) => {
      await probe.tapLeaf(page, 'x');
      await probe.tapLeaf(page, 'e');
      // Without this, the reset below would pass on a page where clicking never revealed anything.
      expect((await labels(probe.leafCaptions(page))).length).toBeGreaterThan(OPENING.length);

      await probe.reset(page);
      expect(await labels(probe.leafCaptions(page))).toEqual(OPENING);
    });
  });
}

/**
 * The two questions only the Cytoscape page can be asked.
 *
 * Both are about a *derivation*. React Flow is handed a `visible` per node and draws exactly those,
 * so neither property can fail there in the way it can here - and the React Flow half of each is a
 * unit test on screen() rather than a page test that could only restate its own input.
 */
test.describe('funcCall on cytoscape, where visibility is derived', () => {
  test.beforeEach(open(cytoscape));

  // Hiding, not removing. Under the old folding this read 11 - which is exactly the trap that made
  // a node count comparable to the payload only after expanding everything first. React Flow leaves
  // a hidden node out of the DOM entirely, so it has no count of its own to compare.
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

  // The state the "never set display on a METHOD node" rule exists for. X1 flows only into X2, both
  // of them inside the methodC boxes nested in methodB, so methodB is drawn purely on the strength of
  // grandchildren. A box whose display came from its own children would go dark here and take the
  // revealed grandchildren with it.
  //
  // Cytoscape-only for a second reason as well as the first: X1 is off screen when it is tapped, and
  // only an emitted event can reach a node that is not drawn. There is no click that gets here.
  test('draws a box whose only revealed nodes are grandchildren', async ({ page }) => {
    await cytoscape.tapLeaf(page, 'X1');

    // Both methodC call sites: the label appears twice, so the tap fans out to both.
    expect(await labels(cytoscape.boxLabels(page))).toEqual(['main', 'methodA', 'methodB', 'methodC', 'methodC']);

    const leaves = await labels(cytoscape.leafCaptions(page));
    expect(leaves).toContain('X1');
    expect(leaves).toContain('X2');
    // Every one of methodB's own children is still hidden - without these the assertion above would
    // hold on a page that reveals far more than it was asked to. `methodB` is in that list twice
    // over: it is the box's RETURN node, and it is also the stub the box would be drawn as were it
    // shut, which a box open through grandchildren alone must not have.
    for (const own of ['methodB', 'd', '11', 'f', '13']) expect(leaves).not.toContain(own);
  });
});
