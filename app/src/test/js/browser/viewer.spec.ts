import { test, expect } from '@playwright/test';
import {
  SURFACE, boxLabels, captionsOf, labels, leafCaptions, open, reset, tapBox, tapLeaf,
} from './page.ts';

// `App` is the opaque node for `new App()`: the class writes no constructor, so there is no body
// to inline and the object is a value from outside. It is a leaf child of `main`, so it opens with
// the rest of the entry method's own nodes.
//
// `methodA` and `methodB` are not bodies: each is the one RETURN node a closed callee is drawn as,
// which is what makes the box exist on the page and therefore clickable.
const OPENING = ['5', '8', 'App', 'app', 'args', 'e', 'main', 'methodA', 'methodB', 'x', 'y'];

/**
 * What a reader sees, asked of the page rather than of the model.
 *
 * Every property here is also a unit test on `screen`, and that is the point of the pair: the unit
 * test says the view is right and this says the page draws it. A page whose data is right and whose
 * rendering is not passes one and fails the other.
 */
test.describe('funcCall', () => {
  test.beforeEach(open('funcCall'));

  test('draws the graph at a usable size', async ({ page }) => {
    const box = await page.locator(SURFACE).first().boundingBox();
    expect(box!.width).toBeGreaterThan(100);
    expect(box!.height).toBeGreaterThan(100);
  });

  test('opens showing the entry method body and each callee as a closed box', async ({ page }) => {
    expect(await labels(leafCaptions(page))).toEqual(OPENING);
    // The two methods main calls, and no deeper: methodB's own callees stay out until it is opened.
    expect(await labels(boxLabels(page))).toEqual(['main', 'methodA', 'methodB']);
    expect(await labels(boxLabels(page))).not.toContain('methodC');
  });

  test('clicking a node reveals its neighbourhood three hops out', async ({ page }) => {
    await tapLeaf(page, 'x');
    const leaves = await labels(leafCaptions(page));
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
    expect(await labels(boxLabels(page))).toEqual(['main', 'methodA', 'methodB']);
  });

  test('reveals accumulate across clicks', async ({ page }) => {
    await tapLeaf(page, 'x');
    const afterX = await labels(leafCaptions(page));
    await tapLeaf(page, 'e');
    const afterE = await labels(leafCaptions(page));

    // Nothing the first click revealed may vanish on the second.
    for (const label of afterX) expect(afterE).toContain(label);
    // And the second click has to actually add something, or the loop above proves nothing.
    expect(afterE.length).toBeGreaterThan(afterX.length);
    expect(afterE).toContain('d');
  });

  test('folding a method box hides its contents, nested boxes and all', async ({ page }) => {
    await tapLeaf(page, 'e');
    // methodB is open, so both of the methodC call sites it holds are offered in turn.
    expect(await labels(boxLabels(page))).toEqual(['main', 'methodA', 'methodB', 'methodC', 'methodC']);

    await tapBox(page, 'methodB');
    // Both methodC boxes go. If the fold used children rather than descendants, whichever one `e`
    // reached would keep its revealed node and survive.
    expect(await labels(boxLabels(page))).toEqual(['main', 'methodA', 'methodB']);
    expect(await labels(leafCaptions(page))).toEqual(OPENING);
  });

  // Without this a value whose story continues off screen is drawn exactly like one that ends here.
  // `x` is passed to methodA, whose body is closed at open, so it has one hidden edge out; `5` flows
  // only into `x`, which is on screen, so it has nothing to declare - and that pairing is what fails
  // if every node is annotated regardless.
  test('a node with hidden neighbours says how many, and one without says nothing', async ({ page }) => {
    expect(await captionsOf(leafCaptions(page), 'x')).toEqual(['x ↓1']);
    expect(await captionsOf(leafCaptions(page), '5')).toEqual(['5']);
  });

  // The half that fails if the badge is computed once at load and never recomputed. Clicking `x`
  // reveals everything within three hops, so nothing adjacent to it is missing any more and the
  // annotation has to go away on its own.
  test('revealing what a node reaches clears its badge', async ({ page }) => {
    expect(await captionsOf(leafCaptions(page), 'x')).toEqual(['x ↓1']);
    await tapLeaf(page, 'x');
    expect(await captionsOf(leafCaptions(page), 'x')).toEqual(['x']);
  });

  test('R returns to the opening set', async ({ page }) => {
    await tapLeaf(page, 'x');
    await tapLeaf(page, 'e');
    // Without this, the reset below would pass on a page where clicking never revealed anything.
    expect((await labels(leafCaptions(page))).length).toBeGreaterThan(OPENING.length);

    await reset(page);
    expect(await labels(leafCaptions(page))).toEqual(OPENING);
  });
});
