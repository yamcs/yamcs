import { DySample } from './DySample';
import { YamcsService } from '../../core/services/YamcsService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Sample, Alarm, ParameterValue } from '@yamcs/client';
import { DyAnnotation } from './DyAnnotation';
import { convertValueToNumber } from '../utils';
import { Subscription } from 'rxjs/Subscription';
import { PlotBuffer, DyValueRange, PlotData } from './PlotBuffer';

/**
 * Stores sample data for use in a ParameterPlot directly
 * in DyGraphs native format.
 *
 * See http://dygraphs.com/data.html#array
 */
export class DyDataSource {

  public loading$ = new BehaviorSubject<boolean>(false);

  data$ = new BehaviorSubject<PlotData>({
    valueRange: [null, null],
    samples: [],
    annotations: [],
  });
  minValue?: number;
  maxValue?: number;

  visibleStart: Date;
  visibleStop: Date;

  private plotBuffer: PlotBuffer;

  private lastLoadPromise: Promise<any> | null;

  // Realtime
  private realtimeSynchronizer: number;
  private realtimeSubscription: Subscription;

  constructor(private yamcs: YamcsService, private qname: string) {
    this.realtimeSynchronizer = window.setInterval(() => {
      if (this.plotBuffer.dirty && !this.loading$.getValue()) {
        const plotData = this.plotBuffer.snapshot();
        this.data$.next({
          samples: plotData.samples,
          annotations: plotData.annotations,
          valueRange: plotData.valueRange,
        });
        this.plotBuffer.dirty = false;
      }
    }, 1000 /* update rate */);

    this.plotBuffer = new PlotBuffer(() => {
      this.reloadVisibleRange();
    });
  }

  /**
   * Triggers a new server request for samples.
   * TODO should pass valueRange somehow
   */
  private reloadVisibleRange() {
    return this.updateWindow(this.visibleStart, this.visibleStop, [null, null]);
  }

  updateWindow(
    start: Date,
    stop: Date,
    valueRange: DyValueRange,
  ) {
    this.loading$.next(true);
    // Load beyond the visible range to be able to show data
    // when panning.
    const delta = stop.getTime() - start.getTime();
    const loadStart = new Date(start.getTime() - delta);
    const loadStop = new Date(stop.getTime() + delta);

    const instanceClient = this.yamcs.getInstanceClient()!;
    const loadPromise = Promise.all([
      instanceClient.getParameterSamples(this.qname, {
        start: loadStart.toISOString(),
        stop: loadStop.toISOString(),
        count: 2000,
      }),
      instanceClient.getAlarmsForParameter(this.qname, {
        start: loadStart.toISOString(),
        stop: loadStop.toISOString(),
      })
    ]);
    this.lastLoadPromise = loadPromise;
    return loadPromise.then(results => {
      // Effectively cancels past requests
      if (this.lastLoadPromise === loadPromise) {
        this.loading$.next(false);
        this.plotBuffer.reset();
        const dySamples = this.processSamples(results[0], start, stop);
        const dyAnnotations = this.spliceAlarmAnnotations(results[1], dySamples);
        this.plotBuffer.setArchiveData(dySamples, dyAnnotations);
        this.plotBuffer.setValueRange(valueRange);
        this.lastLoadPromise = null;
      }
    });
  }

  connectRealtime() {
    this.yamcs.getInstanceClient()!.getParameterValueUpdates({
      id: [{ name: this.qname }],
      sendFromCache: false,
      subscriptionId: -1,
      updateOnExpiration: true,
      abortOnInvalid: true,
    }).then(response => {
      this.realtimeSubscription = response.parameterValues$.subscribe(pvals => {
        const dySample = this.convertParameterValueToSample(pvals[0]);
        if (dySample) {
          this.plotBuffer.addRealtimeValue(dySample);
        }
      });
    });
  }

  disconnect() {
    this.data$.complete();
    this.loading$.complete();
    if (this.realtimeSubscription) {
      this.realtimeSubscription.unsubscribe();
    }
    if (this.realtimeSynchronizer) {
      window.clearInterval(this.realtimeSynchronizer);
    }
  }

  private processSamples(samples: Sample[], start: Date, stop: Date) {
    const dySamples: DySample[] = [];
    this.minValue = undefined;
    this.maxValue = undefined;
    this.visibleStart = start;
    this.visibleStop = stop;
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
        dySamples.push([t, [min, v, max]]);
      } else {
        dySamples.push([t, null]);
      }
    }
    return dySamples;
  }

  private convertParameterValueToSample(pval: ParameterValue): DySample | null {
    const value = convertValueToNumber(pval.engValue);
    if (value !== null) {
      const t = new Date();
      t.setTime(Date.parse(pval.generationTimeUTC));
      if (pval.acquisitionStatus === 'EXPIRED') {
        // We get the last received timestamp.
        // Consider gap to be just after that
        t.setTime(t.getTime() + 1);
        return [t, null]; // Display as gap
      } else if (pval.acquisitionStatus === 'ACQUIRED') {
        return [t, [value, value, value]];
      }
    }
    return null;
  }

  private spliceAlarmAnnotations(alarms: Alarm[], dySamples: DySample[]) {
    const dyAnnotations: DyAnnotation[] = [];
    for (const alarm of alarms) {
      const t = new Date();
      t.setTime(Date.parse(alarm.triggerValue.generationTimeUTC));
      const value = convertValueToNumber(alarm.triggerValue.engValue);
      if (value !== null) {
        const sample: DySample = [t, [value, value, value]];
        const idx = this.findInsertPosition(t, dySamples);
        dySamples.splice(idx, 0, sample);
        dyAnnotations.push({
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
    return dyAnnotations;
  }

  private findInsertPosition(t: Date, dySamples: DySample[]) {
    if (!dySamples.length) {
      return 0;
    }

    for (let i = 0; i < dySamples.length; i++) {
      if (dySamples[i][0] > t) {
        return i;
      }
    }
    return dySamples.length - 1;
  }
}
