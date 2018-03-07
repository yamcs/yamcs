import { Sample } from '../../../yamcs-client/types/monitoring';
import { DySample } from './DySample';

/**
 * Stores sample data for use in a ParameterPlot directly
 * in DyGraphs native format.
 *
 * See http://dygraphs.com/data.html#array
 */
export class DyDataSource {

  data: DySample[] = [];
  rangeMin: number;
  rangeMax: number;

  constructor(samples: Sample[]) {
    for (const sample of samples) {
      const t = new Date();
      t.setTime(Date.parse(sample['time']));
      if (sample.n > 0) {
        const v = sample['avg'];
        const min = sample['min'];
        const max = sample['max'];

        if (this.rangeMin === undefined) {
          this.rangeMin = min;
          this.rangeMax = max;
        } else {
          if (this.rangeMin > min) {
            this.rangeMin = min;
          }
          if (this.rangeMax < max) {
            this.rangeMax = max;
          }
        }
        this.data.push([t, [min, v, max]]);
      } else {
        this.data.push([t, null]);
      }
    }
  }

  connect() {

  }

  disconnect() {

  }
}
