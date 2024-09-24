import { ConfigService, NamedObjectId, ParameterSubscription, ParameterValue, Sample, Synchronizer, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { NamedParameterType } from './NamedParameterType';
import { DyValueRange, PlotBuffer, PlotData } from './PlotBuffer';
import { CustomBarsValue, DySample, DySeries } from './dygraphs';

/**
 * Stores sample data for use in a ParameterPlot directly
 * in DyGraphs native format.
 *
 * See http://dygraphs.com/data.html#array
 */
export class DyDataSource {

  // If true, load more samples than needed
  // (useful when horizontal scroll is allowed)
  extendRequestedRange = true;

  // How many samples to load at once
  resolution = 6000;

  public loading$ = new BehaviorSubject<boolean>(false);

  data$ = new BehaviorSubject<PlotData>({
    valueRange: [null, null],
    samples: [],
  });
  minValue?: number;
  maxValue?: number;

  visibleStart: Date;
  visibleStop: Date;

  parameters$ = new BehaviorSubject<NamedParameterType[]>([]);
  private plotBuffer: PlotBuffer;

  private lastLoadPromise: Promise<any> | null;

  // Realtime
  private realtimeSubscription: ParameterSubscription;
  private syncSubscription: Subscription;
  // Added due to multi-param plots where realtime values are not guaranteed to arrive in the
  // same delivery. Should probably have a server-side solution for this use cause though.
  latestRealtimeValues = new Map<string, CustomBarsValue>();

  private idMapping: { [key: number]: NamedObjectId; };

  constructor(
    private yamcs: YamcsService,
    synchronizer: Synchronizer,
    private configService: ConfigService,
  ) {
    this.syncSubscription = synchronizer.sync(() => {
      if (this.plotBuffer.dirty && !this.loading$.getValue()) {
        const plotData = this.plotBuffer.snapshot();
        this.data$.next({
          samples: plotData.samples,
          valueRange: plotData.valueRange,
        });
        this.plotBuffer.dirty = false;
      }
    });

    this.plotBuffer = new PlotBuffer(() => {
      this.reloadVisibleRange();
    });
  }

  public addParameter(...parameter: NamedParameterType[]) {
    this.parameters$.next([
      ...this.parameters$.value,
      ...parameter,
    ]);

    if (this.realtimeSubscription) {
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

  updateWindowOnly(start: Date, stop: Date) {
    this.visibleStart = start;
    this.visibleStop = stop;
  }

  updateWindow(
    start: Date,
    stop: Date,
    valueRange: DyValueRange,
  ) {
    this.loading$.next(true);
    // Load beyond the visible range to be able to show data
    // when panning.
    const delta = this.extendRequestedRange ? stop.getTime() - start.getTime() : 0;
    const loadStart = new Date(start.getTime() - delta);
    const loadStop = new Date(stop.getTime() + delta);

    const promises: Promise<any>[] = [];
    for (const parameter of this.parameters$.value) {
      promises.push(
        this.yamcs.yamcsClient.getParameterSamples(this.yamcs.instance!, parameter.qualifiedName, {
          start: loadStart.toISOString(),
          stop: loadStop.toISOString(),
          count: this.resolution,
          fields: ['time', 'n', 'avg', 'min', 'max'],
          gapTime: 300000,
          source: this.configService.isParameterArchiveEnabled()
            ? 'ParameterArchive' : 'replay',
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
        const dySeries = [];
        for (let i = 0; i < results.length; i++) {
          dySeries[i] = this.processSamples(results[i]);
        }
        const dySamples = this.mergeSeries(...dySeries);
        this.plotBuffer.setArchiveData(dySamples);
        this.plotBuffer.setValueRange(valueRange);
        this.lastLoadPromise = null;
      }
    });
  }

  private connectRealtime() {
    const ids = this.parameters$.value.map(parameter => ({ name: parameter.qualifiedName }));
    this.realtimeSubscription = this.yamcs.yamcsClient.createParameterSubscription({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
      id: ids,
      sendFromCache: false,
      updateOnExpiration: true,
      abortOnInvalid: true,
      action: 'REPLACE',
    }, data => {
      if (data.mapping) {
        this.idMapping = {
          ...this.idMapping,
          ...data.mapping,
        };
      }
      if (data.values && data.values.length) {
        this.processRealtimeDelivery(data.values);
      }
    });
  }

  addToRealtimeSubscription(ids: NamedObjectId[]) {
    this.realtimeSubscription.sendMessage({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
      id: ids,
      sendFromCache: false,
      updateOnExpiration: true,
      abortOnInvalid: true,
      action: 'ADD',
    });
  }

  /**
   * Emit merged snapsnot (may include values from a previous delivery)
   */
  private processRealtimeDelivery(pvals: ParameterValue[]) {
    for (const pval of pvals) {
      let dyValue: CustomBarsValue = null;
      const value = utils.convertValueToNumber(pval.engValue);
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
      const id = this.idMapping[pval.numericId];
      this.latestRealtimeValues.set(id.name, dyValue);
    }

    const t = new Date();
    t.setTime(Date.parse(pvals[0].generationTime));

    const dyValues: CustomBarsValue[] = this.parameters$.value.map(parameter => {
      return this.latestRealtimeValues.get(parameter.qualifiedName) || null;
    });

    const sample: any = [t, ...dyValues];
    this.plotBuffer.addRealtimeValue(sample);
  }

  disconnect() {
    this.data$.complete();
    this.loading$.complete();
    this.realtimeSubscription?.cancel();
    this.syncSubscription?.unsubscribe();
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

  /**
   * Merges two or more DySample[] series together. This assumes that timestamps between
   * the two series are identical, which is the case if server requests are done
   * with the same date range.
   */
  private mergeSeries(...series: DySeries[]) {
    if (series.length === 1) {
      return series[0];
    }
    let result: DySample[] = series[0];
    for (let i = 1; i < series.length; i++) {
      const merged: DySample[] = [];
      let index1 = 0;
      let index2 = 0;
      let prev1: CustomBarsValue[] = [];
      let prev2: CustomBarsValue | null = null;
      const series1 = result;
      const series2 = series[i];
      while (index1 < series1.length || index2 < series2.length) {
        const top1 = index1 < series1.length ? series1[index1] : null;
        const top2 = index2 < series2.length ? series2[index2] : null;
        if (top1 && top2) {
          if (top1[0].getTime() === top2[0].getTime()) {
            prev1 = top1.slice(1) as CustomBarsValue[];
            prev2 = top2[1];
            merged.push([top1[0], ...prev1, prev2] as any);
            index1++;
            index2++;
          } else if (top1[0].getTime() < top2[0].getTime()) {
            prev1 = top1.slice(1) as CustomBarsValue[];
            merged.push([top1[0], ...prev1, prev2] as any);
            index1++;
          } else {
            prev2 = top2[1];
            merged.push([top2[0], ...prev1, prev2] as any);
            index2++;
          }
        } else if (top1) {
          prev1 = top1.slice(1) as CustomBarsValue[];
          merged.push([top1[0], ...prev1, prev2] as any);
          index1++;
        } else if (top2) {
          prev2 = top2[1];
          merged.push([top2[0], ...prev1, prev2] as any);
          index2++;
        }
      }
      result = merged;
    }
    return result;
  }
}
