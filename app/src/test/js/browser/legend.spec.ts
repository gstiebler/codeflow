import { test, expect, type Page } from '@playwright/test';
import { open } from './page.ts';

const button = (page: Page, name: string) => page.getByRole('button', { name, exact: true });
const panel = (page: Page) => page.getByRole('dialog', { name: 'Legend' });

/** The Legend button on `boundary`, asked of the DOM: closed until asked, and only what the page has. */
test.describe('boundary, legend', () => {
  test.beforeEach(open('boundary'));

  test('Legend opens a panel naming what the page draws, and closes it again', async ({ page }) => {
    await expect(panel(page)).toHaveCount(0);
    await button(page, 'Legend').click();
    await expect(button(page, 'Legend')).toHaveAttribute('aria-pressed', 'true');
    await expect(panel(page)).toContainText('RETURN');
    await expect(panel(page)).toContainText('what a method returns');
    // The pair: a type this fixture never draws is not listed.
    await expect(panel(page)).not.toContainText('UNMODELLED');
    await button(page, 'Legend').click();
    await expect(panel(page)).toHaveCount(0);
  });

  test('the legend lists the boundary outline, and no method box', async ({ page }) => {
    await button(page, 'Legend').click();
    await expect(panel(page)).toContainText('boundary a boundary method');
    await expect(panel(page)).not.toContainText('a method body, inlined per call');
  });
});
