/** React Flow's stylesheet arrives as text, via esbuild's `--loader:.css=text`. */
declare module '*.css' {
  const text: string;
  export default text;
}
