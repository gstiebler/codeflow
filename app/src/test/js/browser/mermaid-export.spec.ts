import { readFileSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
import { open, tapGroupAll } from './page.ts';

const NODE = '.react-flow__node';

const button = (page: Page, name: string) => page.getByRole('button', { name, exact: true });

/** The entities the export writes, back to the characters the page draws. */
const unescape = (label: string) =>
  label.replace(/#quot;/g, '"').replace(/#lt;/g, '<').replace(/#gt;/g, '>').replace(/#35;/g, '#');

/**
 * The Mermaid button on `boundary`, asked of the DOM and of the file the browser saves: the export is
 * what is on screen after the reader's clicks, not the opening.
 */
test.describe('boundary, Mermaid export', () => {
  test.beforeEach(open('boundary'));

  test('after a click, Mermaid downloads codeflow-condensed.md, fenced, with every node drawn', async ({ page }) => {
    await tapGroupAll(page, 'entrypoint');
    const drawn = await page.evaluate((selector) =>
      [...document.querySelectorAll(selector)].map((n) => (n.textContent ?? '').trim()).sort(), NODE);
    // More than the opening's seven, or exporting the opening would pass.
    expect(drawn.length).toBeGreaterThan(7);

    const [download] = await Promise.all([page.waitForEvent('download'), button(page, 'Mermaid').click()]);
    expect(download.suggestedFilename()).toBe('codeflow-condensed.md');
    const text = readFileSync(await download.path(), 'utf8');
    expect(text.startsWith('```mermaid\nflowchart TD\n')).toBe(true);
    expect(text.endsWith('\n```\n')).toBe(true);
    const labels = [...text.matchAll(/^ {2}n\d+\["(.*)"\]$/gm)].map((m) => unescape(m[1])).sort();
    expect(labels.length).toBe(drawn.length);
    expect(labels).toEqual(drawn);
  });
});
