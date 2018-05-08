import sourceMaps from 'rollup-plugin-sourcemaps'
import typescript from 'rollup-plugin-typescript2'
import uglify from 'rollup-plugin-uglify'

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
    uglify(),
    sourceMaps(),
  ],
}
