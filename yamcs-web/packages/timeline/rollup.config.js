import sourceMaps from 'rollup-plugin-sourcemaps';
import { terser } from 'rollup-plugin-terser';
import typescript from 'rollup-plugin-typescript2';

export default {
  input: 'src/index.ts',
  output: [
    { file: 'dist/yamcs-timeline.umd.js', name: 'yamcsTimeline', format: 'umd', sourcemap: true },
    { file: 'dist/yamcs-timeline.es5.js', format: 'es', sourcemap: true },
  ],
  watch: {
    include: 'src/**',
  },
  plugins: [
    typescript({ useTsconfigDeclarationDir: true }),
    terser(),
    sourceMaps(),
  ],
}
