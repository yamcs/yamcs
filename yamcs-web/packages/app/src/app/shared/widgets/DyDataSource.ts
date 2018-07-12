import { Alarm, NamedObjectId, Parameter, ParameterValue, Sample } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { convertValueToNumber } from '../utils';
import { CustomBarsValue, DyAnnotation, DySample } from './dygraphs';
import { DyValueRange, PlotBuffer, PlotData } from './PlotBuffer';

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

  parameters$ = new BehaviorSubject<Parameter[]>([]);
  private plotBuffer: PlotBuffer;

  private lastLoadPromise: Promise<any> | null;

  // Realtime
  private subscriptionId: number;
  private realtimeSynchronizer: number;
  private realtimeSubscription: Subscription;
  // Added due to multi-param plots where realtime values are not guaranteed to arrive in the
  // same delivery. Should probably have a server-side solution for this use cause though.
  latestRealtimeValues = new Map<string, CustomBarsValue>();

  constructor(private yamcs: YamcsService) {
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

  public addParameter(...parameter: Parameter[]) {
    this.parameters$.next([
      ...this.parameters$.value,
      ...parameter,
    ]);

    if (this.subscriptionId) {
      const ids = parameter.map(p => ({ name: p.qualifiedName }));
      this.addToRealtimeSubscription(ids);
    } else {
      this.connectRealtime();
    }
  }

  public removeParameter(qualifiedName: string) {
    const parameters = this.parameters$.value.filter(p => p.qualifiedName !== qualifiedName);
    this.parameters$.next(parameters);
  }

  /**
   * Triggers a new server request for samples.
   * TODO should pass valueRange somehow
   */
  reloadVisibleRange() {
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
    const promises: Promise<any>[] = [];
    for (const parameter of this.parameters$.value) {
      promises.push(
        instanceClient.getParameterSamples(parameter.qualifiedName, {
          start: loadStart.toISOString(),
          stop: loadStop.toISOString(),
          count: 6000,
        }),
        instanceClient.getAlarmsForParameter(parameter.qualifiedName, {
          start: loadStart.toISOString(),
          stop: loadStop.toISOString(),
        })
      );
    }

    const loadPromise = Promise.all(promises);
    this.lastLoadPromise = loadPromise;
    return loadPromise.then(results => {
      // Effectively cancels past requests
      if (this.lastLoadPromise === loadPromise) {
        this.loading$.next(false);
        this.plotBuffer.reset();
        this.latestRealtimeValues.clear();
        this.visibleStart = start;
        this.visibleStop = stop;
        this.minValue = undefined;
        this.maxValue = undefined;
        const dySamples = this.processSamples(results[0]);
        const dyAnnotations = this.spliceAlarmAnnotations([] /*results[1] TODO */, dySamples);
        for (let i = 1; i < this.parameters$.value.length; i++) {
          this.mergeSeries(dySamples, this.processSamples(results[2 * i]));
          // const seriesAnnotations = this.spliceAlarmAnnotations(results[2 * i + 1], seriesSamples);
        }
        this.plotBuffer.setArchiveData(dySamples, dyAnnotations);
        this.plotBuffer.setValueRange(valueRange);
        this.lastLoadPromise = null;
      }
    });
  }

  private connectRealtime() {
    const ids = this.parameters$.value.map(parameter => ({ name: parameter.qualifiedName }));
    this.yamcs.getInstanceClient()!.getParameterValueUpdates({
      id: ids,
      sendFromCache: false,
      subscriptionId: -1,
      updateOnExpiration: true,
      abortOnInvalid: true,
    }).then(response => {
      this.subscriptionId = response.subscriptionId;
      this.realtimeSubscription = response.parameterValues$.subscribe(pvals => {
        this.processRealtimeDelivery(pvals);
      });
    });
  }

  addToRealtimeSubscription(ids: NamedObjectId[]) {
    this.yamcs.getInstanceClient()!.getParameterValueUpdates({
      id: ids,
      sendFromCache: false,
      subscriptionId: this.subscriptionId,
      updateOnExpiration: true,
      abortOnInvalid: true,
    });
  }

  /**
   * Emit merged snapsnot (may include values from a previous delivery)
   */
  private processRealtimeDelivery(pvals: ParameterValue[]) {
    for (const pval of pvals) {
      let dyValue: CustomBarsValue = null;
      const value = convertValueToNumber(pval.engValue);
      if (value !== null) {
        if (pval.acquisitionStatus === 'EXPIRED') {
          // We get the last received timestamp.
          // Consider gap to be just after that
          /// t.setTime(t.getTime() + 1); // TODO Commented out because we need identical timestamps in case of multi param plots
          dyValue = null; // Display as gap
        } else if (pval.acquisitionStatus === 'ACQUIRED') {
          dyValue = [value, value, value];
        }
      }
      this.latestRealtimeValues.set(pval.id.name, dyValue);
    }

    const t = new Date();
    t.setTime(Date.parse(pvals[0].generationTimeUTC));

    const dyValues: CustomBarsValue[] = this.parameters$.value.map(parameter => {
      return this.latestRealtimeValues.get(parameter.qualifiedName) || null;
    });

    const sample: any = [t, ...dyValues];
    this.plotBuffer.addRealtimeValue(sample);
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

  private processSamples(samples: Sample[]) {
    const dySamples: DySample[] = [];
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
          series: this.parameters$.value[0].qualifiedName,
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

  /**
   * Merges two DySample[] series together. This assumes that timestamps between
   * the two series are identical, which is the case if server requests are done
   * with the same date range.
   */
  private mergeSeries(samples1: DySample[], samples2: DySample[]) {
    if (samples1.length !== samples2.length) {
      throw new Error('Cannot merge two sample arrays of unequal length');
    }
    for (let i = 0; i < samples1.length; i++) {
      samples1[i].push(samples2[i][1]);
    }
  }
}
