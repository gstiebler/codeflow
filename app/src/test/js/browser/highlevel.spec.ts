import { test, expect } from '@playwright/test';
import { drewAgain, open } from './page.ts';

test.describe('high-level zoom levels (Architecture / Workflow / Expressions)', () => {
  test.beforeEach(open('highLevel'));

  test('opens directly in Architecture level with entrypoint route, entity card, assumed DI service, throw, and noise hidden', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Architecture', exact: true })).toHaveAttribute('aria-pressed', 'true');
    // HTTP entrypoint label from @RequestMapping + @PostMapping
    await expect(page.locator('.cf-node').filter({ hasText: 'POST /loans/repay' })).toHaveCount(1);
    // Domain Entity State Card for Loan
    const entity = page.locator('.cf-entity').filter({ hasText: 'Loan' });
    await expect(entity).toHaveCount(1);
    await expect(entity).toContainText('writes: balance');
    // Assumed single-impl DI service rendered with dashed amber border and summary subtitle
    const service = page.locator('.cf-group').filter({ hasText: 'LoanServiceImpl#apply' });
    await expect(service).toHaveCount(1);
    await expect(service).toContainText('assumed: single impl');
    await expect(service).toHaveCSS('border-top-style', 'dashed');
    await expect(service).toHaveCSS('border-top-color', 'rgb(230, 81, 0)');
    // Throw node rendered
    await expect(page.locator('.cf-node').filter({ hasText: 'throw IllegalArgumentException' })).toHaveCount(1);
    // Noise logger hidden by default, shown when Noise button is toggled
    await expect(page.locator('.cf-group').filter({ hasText: 'Logger#info' })).toHaveCount(0);
    await drewAgain(page, () => page.getByRole('button', { name: 'Noise', exact: true }).click());
    await expect(page.locator('.cf-group').filter({ hasText: 'Logger#info' })).toHaveCount(1);
  });

  test('switching between Architecture, Workflow, and Expressions updates the DOM cleanly', async ({ page }) => {
    // Switch to Workflow (Level 2)
    await drewAgain(page, () => page.getByRole('button', { name: 'Workflow', exact: true }).click());
    await expect(page.getByRole('button', { name: 'Workflow', exact: true })).toHaveAttribute('aria-pressed', 'true');
    // Callee LoanServiceImpl#apply is folded as a Summary Card in Workflow view
    const callee = page.locator('.cf-group').filter({ hasText: 'LoanServiceImpl#apply' });
    await expect(callee).toHaveCount(1);
    await expect(callee).toContainText('writes: Loan.balance');

    // Clicking the folded Summary Card opens LoanServiceImpl#apply inside Workflow view
    await drewAgain(page, () => callee.click());
    await expect(page.locator('.cf-frame').filter({ hasText: 'apply (assumed)' })).toHaveCount(1);
    await expect(page.locator('.cf-node').filter({ hasText: 'Loan.balance' }).first()).toBeVisible();

    // Switch to Expressions (Level 3)
    await drewAgain(page, () => page.getByRole('button', { name: 'Expressions', exact: true }).click());
    await expect(page.getByRole('button', { name: 'Expressions', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('.cf-group').filter({ hasText: /^program \(/ })).toHaveCount(1);
  });

  test('clicking a Service card in Architecture drills into Workflow focused on that service', async ({ page }) => {
    const service = page.locator('.cf-group').filter({ hasText: 'LoanServiceImpl#apply' });
    await drewAgain(page, () => service.click());
    await expect(page.getByRole('button', { name: 'Workflow', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('.cf-frame').filter({ hasText: 'apply (assumed)' })).toHaveCount(1);
  });
});
