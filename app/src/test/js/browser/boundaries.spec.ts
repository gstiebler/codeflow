import { test, expect } from '@playwright/test';
import { drewAgain, open } from './page.ts';

const GROUP = '.react-flow__node .cf-group';
const PANEL = '[aria-label="Boundaries"]';
const BUTTON = { name: 'Boundaries', exact: true };

/**
 * The toolbar's Boundaries list on `boundaryBranch`: 14 boundary call sites of `Port` (open, send,
 * close, flush), none on screen at the opening, which draws only the entry box and the program - two groups.
 */
test.describe('boundaryBranch, the Boundaries list', () => {
  test.beforeEach(open('boundaryBranch'));

  test('the button opens a panel with a row per call site, under one heading per class', async ({ page }) => {
    await expect(page.getByRole('button', BUTTON)).toHaveAttribute('aria-pressed', 'false');
    await page.getByRole('button', BUTTON).click();
    await expect(page.getByRole('button', BUTTON)).toHaveAttribute('aria-pressed', 'true');
    const panel = page.locator(PANEL);
    await expect(panel.locator('b')).toHaveText('Boundaries (14)');
    await expect(panel.locator('h4')).toHaveText(['Port']);
    await expect(panel.locator('.cf-boundary-row')).toHaveCount(14);
    await expect(panel.locator('.cf-boundary-row:disabled')).toHaveCount(0);
    // 14 rows is past 10: a filter, by label.
    await panel.getByRole('searchbox').fill('flush');
    await expect(panel.locator('.cf-boundary-row')).toHaveCount(2);
  });

  test('a click on a row adds that one box, marks the row and leaves the panel open', async ({ page }) => {
    await expect(page.locator(GROUP)).toHaveCount(2);
    await page.getByRole('button', BUTTON).click();
    const panel = page.locator(PANEL);
    await drewAgain(page, () => panel.locator('.cf-boundary-row').first().click());
    await expect(page.locator(GROUP)).toHaveCount(3);
    await expect(panel).toBeVisible();
    await expect(panel.locator('.cf-boundary-row')).toHaveCount(14);
    await expect(panel.locator('.cf-boundary-row:disabled')).toHaveCount(1);
    await expect(panel.locator('.cf-boundary-row:disabled .cf-boundary-mark')).toHaveText('✓');
    // A second row adds a second box.
    await drewAgain(page, () => panel.locator('.cf-boundary-row:not(:disabled)').first().click());
    await expect(page.locator(GROUP)).toHaveCount(4);
    await expect(panel.locator('.cf-boundary-row:disabled')).toHaveCount(2);
  });

  test('Escape, the x and the button close it; a pane click does not; Reset leaves it as it is', async ({ page }) => {
    const panel = page.locator(PANEL);
    await page.getByRole('button', BUTTON).click();
    await page.mouse.click(600, 500);
    await expect(panel).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(panel).toHaveCount(0);
    await page.getByRole('button', BUTTON).click();
    await panel.getByRole('button', { name: 'Close', exact: true }).click();
    await expect(panel).toHaveCount(0);
    await page.getByRole('button', BUTTON).click();
    await drewAgain(page, () => panel.locator('.cf-boundary-row').first().click());
    await drewAgain(page, () => page.getByRole('button', { name: 'Reset' }).click());
    await expect(panel).toBeVisible();
    await expect(page.locator(GROUP)).toHaveCount(2);
    await expect(panel.locator('.cf-boundary-row:disabled')).toHaveCount(0);
    await page.getByRole('button', BUTTON).click();
    await expect(panel).toHaveCount(0);
  });
});

test.describe('funcCall, no boundary', () => {
  test.beforeEach(open('funcCall'));

  test('the Boundaries button cannot be used', async ({ page }) => {
    await expect(page.getByRole('button', BUTTON)).toBeDisabled();
  });
});
