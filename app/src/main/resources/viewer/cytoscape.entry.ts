/**
 * What the bundle runs. The page has one script and no module scope to call into, so the entry
 * point reads the payload the exporter substituted and starts the renderer itself.
 */
import { init } from './cytoscape.ts';
import type { Payload } from './types.ts';

declare global { interface Window { __PAYLOAD__: Payload } }
init(window.__PAYLOAD__);
