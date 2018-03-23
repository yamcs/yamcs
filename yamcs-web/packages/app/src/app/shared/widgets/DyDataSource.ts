import { DySample } from './DySample';
import { YamcsService } from '../../core/services/YamcsService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Sample, Alarm } from '@yamcs/client';
import { DyAnnotation } from './DyAnnotation';
import { convertValueToNumber } from '../utils';

export class DyDataUpdate {
  samples: DySample[];
  annotations: DyAnnotation[];

  restoreValueRange?: [number, number];
}

/**
 * Stores sample data for use in a ParameterPlot directly
 * in DyGraphs native format.
 *
 * See http://dygraphs.com/data.html#array
 */
export class DyDataSource {

  public loading$ = new BehaviorSubject<boolean>(false);

  data$ = new BehaviorSubject<DyDataUpdate>({
    samples: [],
    annotations: [],
  });
  minValue?: number;
  maxValue?: number;

  visibleStart: Date;
  visibleStop: Date;

  private dySamples: DySample[] = [];
  private dyAnnotations: DyAnnotation[] = [];

  private lastLoadPromise: Promise<any> | null;

  constructor(private yamcs: YamcsService, private qname: string) {
  }

  setDateWindow(
    start: Date,
    stop: Date,
    restoreValueRange?: [number, number],
  ) {
    this.loading$.next(true);
    // Load beyond the visible range to be able to show data
    // when panning.
    const delta = stop.getTime() - start.getTime();
    const loadStart = new Date(start.getTime() - delta);
    const loadStop = new Date(stop.getTime() + delta);

    const instanceClient = this.yamcs.getSelectedInstance();
    const loadPromise = Promise.all([
      instanceClient.getParameterSamples(this.qname, {
        start: loadStart.toISOString(),
        stop: loadStop.toISOString(),
        count: 3000,
      }),
      instanceClient.getAlarmsForParameter(this.qname, {
        start: loadStart.toISOString(),
        stop: loadStop.toISOString(),
      })
    ]);
    this.lastLoadPromise = loadPromise;
    loadPromise.then(results => {
      // Effectively cancels past requests
      if (this.lastLoadPromise === loadPromise) {
        this.loading$.next(false);
        const samples = results[0];
        const alarms = results[1];
        this.processSamples(samples, start, stop);
        this.spliceAlarmAnnotations(alarms);

        this.data$.next({
          samples: this.dySamples,
          annotations: this.dyAnnotations,
          restoreValueRange,
        });
        this.lastLoadPromise = null;
      }
    });
  }

  disconnect() {
    this.data$.complete();
    this.loading$.complete();
  }

  private processSamples(samples: Sample[], start: Date, stop: Date) {
    this.minValue = undefined;
    this.maxValue = undefined;
    this.visibleStart = start;
    this.visibleStop = stop;
    this.dySamples.length = 0;
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
        this.dySamples.push([t, [min, v, max]]);
      } else {
        this.dySamples.push([t, null]);
      }
    }
  }

  private spliceAlarmAnnotations(alarms: Alarm[]) {
    this.dyAnnotations.length = 0;
    for (const alarm of alarms) {
      const t = new Date();
      t.setTime(Date.parse(alarm.triggerValue.generationTimeUTC));
      const value = convertValueToNumber(alarm.triggerValue.engValue);
      if (value !== null) {
        const sample: DySample = [t, [value, value, value]];
        const idx = this.findInsertPosition(t);
        this.dySamples.splice(idx, 0, sample);
        this.dyAnnotations.push({
          series: this.qname,
          x: t.getTime(),
          shortText: 'A',
          text: 'Alarm triggered at ' + alarm.triggerValue.generationTimeUTC,
          tickHeight: 1,
          cssClass: 'annotation',
          tickColor: 'red',
          // attachAtBottom: true,
        });
      }
    }
  }

  private findInsertPosition(t: Date) {
    if (!this.dySamples.length) {
      return 0;
    }

    for (let i = 0; i < this.dySamples.length; i++) {
      if (this.dySamples[i][0] > t) {
        return i;
      }
    }
    return this.dySamples.length - 1;
  }
}
