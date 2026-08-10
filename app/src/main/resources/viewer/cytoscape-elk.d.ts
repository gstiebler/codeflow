/**
 * cytoscape-elk ships no types. It is a Cytoscape extension and the only thing done with it is
 * handing it to `cytoscape.use`, so the shape below is the whole of what this page depends on.
 */
declare module 'cytoscape-elk' {
  import type { Ext } from 'cytoscape';
  const extension: Ext;
  export default extension;
}
