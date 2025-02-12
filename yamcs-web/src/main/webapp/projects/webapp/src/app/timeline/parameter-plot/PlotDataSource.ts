import { ConfigService, NamedObjectId, ParameterSubscription, ParameterValue, Sample, Synchronizer, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { DyValueRange } from '../../shared/parameter-plot/DyPlotBuffer';
import { NamedParameterType } from '../../shared/parameter-plot/NamedParameterType';
import { PlotBuffer, PlotData } from './PlotBuffer';

export type CustomBarsValue = [number, number, number] | null;

export type PlotSample = [Date, CustomBarsValue];

export type PlotSeries = Sample[];

/**
 * Stores sample data for use in a ParameterPlot.
 */
export class PlotDataSource {

  // How many samples to load at once
  resolution = 6000;

  public loading$ = new BehaviorSubject<boolean>(false);

  data$ = new BehaviorSubject<PlotData>({
    valueRange: [null, null],
    series: [],
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
    this.syncSubscription = synchronizer.syncFast(() => this.plotNow());

    this.plotBuffer = new PlotBuffer(() => {
      this.reloadVisibleRange();
    });
  }

  private plotNow() {
    if (this.plotBuffer.dirty && !this.loading$.getValue()) {
      const plotData = this.plotBuffer.snapshot();
      this.data$.next(plotData);
      this.plotBuffer.dirty = false;
    }
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
    const loadStart = new Date(start.getTime());
    const loadStop = new Date(stop.getTime());

    const parameters = this.parameters$.value;
    const promises: Promise<any>[] = [];
    for (const parameter of parameters) {
      promises.push(
        this.yamcs.yamcsClient.getParameterSamples(this.yamcs.instance!, parameter.qualifiedName, {
          start: loadStart.toISOString(),
          stop: loadStop.toISOString(),
          count: this.resolution,
          fields: ['time', 'n', 'avg', 'min', 'max', 'firstTime', 'lastTime'],
          gapTime: 300000,
          source: this.configService.isParameterArchiveEnabled()
            ? 'ParameterArchive' : 'replay',
        })
      );
    }

    const loadPromise = Promise.allSettled(promises);
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
          const result = results[i];
          if (result.status === 'fulfilled') {
            dySeries.push(this.processSamples(result.value));
          } else {
            console.warn(`Failed to retrieve samples for ${parameters[i].qualifiedName}`, result.reason);
            dySeries.push([]);
          }
        }
        this.plotBuffer.setArchiveData(dySeries);
        this.plotBuffer.setValueRange(valueRange);
        // Quick emit, don't wait on sync tick
        this.plotNow();
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
    // Ensure the initial reply is already received
    this.realtimeSubscription.addReplyListener(() => {
      this.realtimeSubscription.sendMessage({
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor!,
        id: ids,
        sendFromCache: false,
        updateOnExpiration: true,
        abortOnInvalid: true,
        action: 'ADD',
      });
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
    const plotSeries: PlotSeries = [];
    for (const sample of samples) {
      const t = new Date();
      t.setTime(Date.parse(sample.time));
      if (sample.n > 0) {
        const min = sample.min;
        const max = sample.max;

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
      }
      plotSeries.push(sample);
    }
    return plotSeries;
  }
}
