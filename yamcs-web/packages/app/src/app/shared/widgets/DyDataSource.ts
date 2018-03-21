import { DySample } from './DySample';
import { YamcsService } from '../../core/services/YamcsService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Sample, Alarm } from '@yamcs/client';
import { DyAnnotation } from './DyAnnotation';

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

  private loadOffscreen = true;

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

    const instanceClient = this.yamcs.getSelectedInstance();
    return Promise.all([
      instanceClient.getParameterSamples(this.qname, {
        start: loadStart.toISOString(),
        stop: loadStop.toISOString(),
        count: 3000,
      }),
      instanceClient.getAlarmsForParameter(this.qname, {
        start: loadStart.toISOString(),
        stop: loadStop.toISOString(),
      })
    ]).then(results => {
      const samples = results[0];
      const alarms = results[1];
      this.processSamples(samples, start, stop);
      this.spliceAlarmAnnotations(alarms);

      this.data$.next({
        samples: this.dySamples,
        annotations: this.dyAnnotations,
        restoreValueRange,
      });
    });
  }

  connect() {

  }

  disconnect() {

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
      const closest = this.findClosestSample(t);
      if (closest && closest[1] !== null) { // Exclude if associated to a gap, these cannot be attached properly
        this.dyAnnotations.push({
          series: this.qname,
          x: closest[0].getTime(),
          shortText: alarm.triggerValue.monitoringResult,
          text: alarm.triggerValue.generationTimeUTC + ': ' + alarm.triggerValue.engValue.uint32Value + '(associated with  ' + closest[1] + ')',
          tickHeight: 0,
          cssClass: 'annotation',
          // tickColor: red,
          // attachAtBottom: true,
        });
      }
    }
  }

  // Assumes samples are sorted
  private findClosestSample(t: Date) {
    if (!this.dySamples.length) {
      return null;
    }
    if (this.dySamples.length === 1) {
      return this.dySamples[0];
    }

    for (let i = 1; i < this.dySamples.length; i++) {
      const dySample = this.dySamples[i];
      // As soon as a number bigger than target is found, return the previous or current
      // number depending on which has smaller difference to the target.
      if (dySample[0] > t) {
        const prev = this.dySamples[i - 1];
        return Math.abs(prev[0].getTime() - t.getTime()) < Math.abs(dySample[0].getTime() - t.getTime()) ? prev : dySample;
      }
    }
    return this.dySamples[this.dySamples.length - 1];
  }
}
