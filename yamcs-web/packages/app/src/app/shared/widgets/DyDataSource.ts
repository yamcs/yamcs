import { DySample } from './DySample';
import { YamcsService } from '../../core/services/YamcsService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

export class DyDataUpdate {
  samples: DySample[];

  // Signals when a value
  restoreValueRange?: [number, number];
}

/**
 * Stores sample data for use in a ParameterPlot directly
 * in DyGraphs native format.
 *
 * See http://dygraphs.com/data.html#array
 */
export class DyDataSource {

  private loadOffscreen = true;

  data$ = new BehaviorSubject<DyDataUpdate>({
    samples: [],
  });
  minValue?: number;
  maxValue?: number;

  visibleStart: Date;
  visibleStop: Date;

  private data: DySample[] = [];

  constructor(private yamcs: YamcsService, private qname: string) {
  }

  setDateWindow(
    start: Date,
    stop: Date,
    restoreValueRange?: [number, number],
  ) {
    // Load beyond the visible range to be able to show data
    // when panning.
    let loadStart = start;
    let loadStop = stop;
    if (this.loadOffscreen) {
      const delta = stop.getTime() - start.getTime();
      loadStart = new Date(start.getTime() - delta);
      loadStop = new Date(stop.getTime() + delta);
    }

    return this.yamcs.getSelectedInstance().getParameterSamples(this.qname, {
      start: loadStart.toISOString(),
      stop: loadStop.toISOString(),
      count: 3000,
    }).then(samples => {
      this.minValue = undefined;
      this.maxValue = undefined;
      this.visibleStart = start;
      this.visibleStop = stop;
      this.data.length = 0;
      for (const sample of samples) {
        const t = new Date();
        t.setTime(Date.parse(sample['time']));
        if (sample.n > 0) {
          const v = sample['avg'];
          const min = sample['min'];
          const max = sample['max'];

          if (this.minValue === undefined) {
            this.minValue = min;
            this.maxValue = max;
          } else {
            if (this.minValue > min) {
              this.minValue = min;
            }
            if (this.maxValue! < max) {
              this.maxValue = max;
            }
          }
          this.data.push([t, [min, v, max]]);
        } else {
          this.data.push([t, null]);
        }
      }
      this.data$.next({ samples: this.data, restoreValueRange });
      return this.data;
    });
  }

  connect() {

  }

  disconnect() {

  }
}
