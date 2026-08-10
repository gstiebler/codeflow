/**
 * Bundles the renderer into one IIFE that HtmlExporter inlines.
 *
 * Committed output, so that `./gradlew build` never needs npm - the same contract the hand-vendored
 * libraries had. `npm test` rebuilds into a temp directory and compares, because a stale bundle is a
 * page rendering yesterday's model while every test passes on today's source.
 *
 * A list of one, because the shape is the point: a bundle is an entry point plus an output name, and
 * a second page is a line here rather than a rewrite.
 *
 * Pass a directory to write elsewhere; that is what the freshness check does.
 */
import { build } from 'esbuild';
import { join } from 'node:path';

const VIEWER = 'app/src/main/resources/viewer';

export const BUNDLES = [
  { entry: `${VIEWER}/reactflow.entry.tsx`, out: 'reactflow.bundle.js' },
];

export async function buildViewer(outdir = VIEWER) {
  for (const { entry, out } of BUNDLES) {
    await build({
      entryPoints: [entry],
      outfile: join(outdir, out),
      bundle: true,
      format: 'iife',
      minify: true,
      target: 'es2020',
      jsx: 'automatic',
      // The CSS arrives as a string the renderer puts in a <style>, so the page stays one file.
      loader: { '.css': 'text' },
      // React ships both branches of this and picks at runtime; without it the dev build is bundled,
      // which is three times the size and warns in the console.
      define: { 'process.env.NODE_ENV': '"production"' },
      logLevel: 'warning',
    });
  }
}

if (import.meta.filename === process.argv[1]) await buildViewer();
