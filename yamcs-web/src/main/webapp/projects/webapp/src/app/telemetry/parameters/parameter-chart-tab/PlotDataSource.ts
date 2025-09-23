import {
  ConfigService,
  MeanSample,
  NamedObjectId,
  ParameterSubscription,
  ParameterValue,
  Synchronizer,
  YamcsService,
  utils,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { NamedSeries, PlotBuffer, PlotData } from './PlotBuffer';
import { PlotPoint } from './PlotPoint';
import { TraceConfig } from './TraceConfig';

/**
 * Stores sample data for use in a ParameterPlot.
 */
export class PlotDataSource {
  // How many samples to load at once
  resolution = 6000;

  public loading$ = new BehaviorSubject<boolean>(false);

  data$ = new BehaviorSubject<PlotData[]>([]);

  visibleStart: Date;
  visibleStop: Date;

  // Keep track of current config. Note that multiple traces may be
  // using the same underlying parameter.
  private traceById = new Map<string, TraceConfig>();
  private plotBuffer: PlotBuffer;

  private lastLoadPromise: Promise<any> | null;

  private realtimeSubscription: ParameterSubscription;
  private syncSubscription: Subscription;
  private idMapping: { [key: number]: NamedObjectId };

  latestRealtimeRawValues = new Map<string, number>();
  latestRealtimeEngValues = new Map<string, number>();

  constructor(
    private yamcs: YamcsService,
    synchronizer: Synchronizer,
    private configService: ConfigService,
  ) {
    this.syncSubscription = synchronizer.sync(() => {
      this.emitDataUpdate();
    });
    this.plotBuffer = new PlotBuffer(() => {
      this.reloadVisibleRange();
    });
  }

  private emitDataUpdate() {
    if (!this.loading$.getValue()) {
      const visibleStart = this.visibleStart.getTime();
      const visibleStop = this.visibleStop.getTime();
      const plotData = this.plotBuffer.snapshot(visibleStart, visibleStop);
      this.data$.next(plotData);
    }
  }

  public addOrUpdateTrace(traceId: string, config: TraceConfig) {
    const oldConfig = this.traceById.get(traceId);

    const parameterChanged =
      !!oldConfig?.parameter && oldConfig?.parameter !== config.parameter;
    if (parameterChanged) {
      this.plotBuffer.clearArchiveData(traceId);
    }

    this.traceById.set(traceId, config);

    if (this.realtimeSubscription) {
      this.updateRealtimeSubscription();
    } else {
      this.connectRealtime();
    }

    this.reloadVisibleRange();
  }

  public removeTrace(traceId: string) {
    const trace = this.traceById.get(traceId);
    if (trace) {
      this.plotBuffer.clearArchiveData(traceId);
      this.traceById.delete(traceId);
      const qualifiedName = utils.getMemberPath(trace.parameter)!;
      this.latestRealtimeRawValues.delete(qualifiedName);
      this.latestRealtimeEngValues.delete(qualifiedName);
      this.emitDataUpdate();
    }
  }

  /**
   * Triggers a new server request for samples.
   */
  reloadVisibleRange() {
    if (this.visibleStart !== undefined && this.visibleStop !== undefined) {
      return this.updateWindow(this.visibleStart, this.visibleStop, true);
    }
  }

  updateWindowOnly(start: Date, stop: Date) {
    this.visibleStart = start;
    this.visibleStop = stop;
  }

  updateWindow(start: Date, stop: Date, fetch: boolean) {
    if (this.configService.getConfig().tmArchive && fetch) {
      this.loading$.next(true);
      // Load some offscreen data to reduce chances of being able to connect
      // an offscreen point with the start of the visible plot line.
      const offscreenEdge = (stop.getTime() - start.getTime()) / 10;
      const loadStart = new Date(start.getTime() - offscreenEdge);
      const loadStop = new Date(stop.getTime());

      const traceIds = [...this.traceById.keys()];
      const traceConfigs = [...this.traceById.values()];
      const promises: Promise<any>[] = [];
      for (const traceConfig of traceConfigs) {
        const qualifiedName = utils.getMemberPath(traceConfig.parameter)!;
        promises.push(
          this.yamcs.yamcsClient.downsampleMean(
            this.yamcs.instance!,
            qualifiedName,
            {
              start: loadStart.toISOString(),
              stop: loadStop.toISOString(),
              count: this.resolution,
              fields: [
                'time',
                'n',
                'avg',
                'min',
                'max',
                'firstTime',
                'lastTime',
              ],
              gapTime: 300000,
              useRawValue: traceConfig.valueType === 'raw',
              source: this.configService.isParameterArchiveEnabled()
                ? 'ParameterArchive'
                : 'replay',
            },
          ),
        );
      }

      const loadPromise = Promise.allSettled(promises);
      this.lastLoadPromise = loadPromise;
      return loadPromise.then((results) => {
        // Effectively cancels past requests
        if (this.lastLoadPromise === loadPromise) {
          this.loading$.next(false);
          this.plotBuffer.reset();
          this.visibleStart = start;
          this.visibleStop = stop;
          const namedSeries: NamedSeries[] = [];
          for (let i = 0; i < results.length; i++) {
            const traceConfig = traceConfigs[i];
            const result = results[i];
            const qualifiedName = utils.getMemberPath(traceConfig.parameter)!;
            if (result.status === 'fulfilled') {
              namedSeries.push({
                traceId: traceIds[i],
                name: qualifiedName,
                valueType: traceConfig.valueType,
                series: this.processSamples(result.value),
              });
            } else {
              console.warn(
                `Failed to retrieve samples for ${qualifiedName}`,
                result.reason,
              );
              namedSeries.push({
                traceId: traceIds[i],
                name: qualifiedName,
                valueType: traceConfig.valueType,
                series: [],
              });
            }
          }
          this.plotBuffer.setArchiveData(namedSeries);
          // Quick emit, don't wait on sync tick
          this.emitDataUpdate();
          this.lastLoadPromise = null;
        }
      });
    } else if (fetch) {
      if (fetch) {
        this.plotBuffer.reset();
      }
      this.visibleStart = start;
      this.visibleStop = stop;
      // Even though there's no archive, pass series
      // information to PlotBuffer. It uses it to order
      // data when snapshotting.
      const namedSeries: NamedSeries[] = [];
      const traceIds = [...this.traceById.keys()];
      const traceConfigs = [...this.traceById.values()];
      for (let i = 0; i < traceConfigs.length; i++) {
        const traceConfig = traceConfigs[i];
        const qualifiedName = utils.getMemberPath(traceConfig.parameter)!;
        namedSeries.push({
          traceId: traceIds[i],
          name: qualifiedName,
          valueType: traceConfig.valueType,
          series: [],
        });
      }
      this.plotBuffer.setArchiveData(namedSeries);
      // Quick emit, don't wait on sync tick
      this.emitDataUpdate();
    } else {
      this.visibleStart = start;
      this.visibleStop = stop;
    }
  }

  private connectRealtime() {
    const qualifiedNames = new Set<string>();
    this.traceById.forEach((trace) => {
      const qualifiedName = utils.getMemberPath(trace.parameter)!;
      qualifiedNames.add(qualifiedName);
    });
    this.realtimeSubscription =
      this.yamcs.yamcsClient.createParameterSubscription(
        {
          instance: this.yamcs.instance!,
          processor: this.yamcs.processor!,
          id: [...qualifiedNames].map((x) => ({ name: x })),
          sendFromCache: false,
          updateOnExpiration: true,
          abortOnInvalid: true,
          action: 'REPLACE',
        },
        (data) => {
          if (data.mapping) {
            this.idMapping = {
              ...this.idMapping,
              ...data.mapping,
            };
          }
          if (data.values?.length) {
            this.processRealtimeDelivery(data.values);
          }
        },
      );
  }

  private updateRealtimeSubscription() {
    // Ensure the initial reply is already received
    this.realtimeSubscription.addReplyListener(() => {
      const qualifiedNames = new Set<string>();
      this.traceById.forEach((trace) => {
        const qualifiedName = utils.getMemberPath(trace.parameter)!;
        qualifiedNames.add(qualifiedName);
      });

      this.realtimeSubscription.sendMessage({
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor!,
        id: [...qualifiedNames].map((x) => ({ name: x })),
        sendFromCache: false,
        updateOnExpiration: true,
        abortOnInvalid: true,
        action: 'REPLACE',
      });
    });
  }

  private processRealtimeDelivery(pvals: ParameterValue[]) {
    for (const pval of pvals) {
      const qualifiedName = this.idMapping[pval.numericId].name;
      const time = Date.parse(pval.generationTime);

      const rawValue =
        pval.rawValue && pval.acquisitionStatus === 'ACQUIRED'
          ? utils.convertValueToNumber(pval.rawValue)
          : null;
      if (rawValue === null) {
        this.latestRealtimeRawValues.delete(qualifiedName);
      } else {
        this.latestRealtimeRawValues.set(qualifiedName, rawValue);
      }

      const engValue =
        pval.engValue && pval.acquisitionStatus === 'ACQUIRED'
          ? utils.convertValueToNumber(pval.engValue)
          : null;
      if (engValue === null) {
        this.latestRealtimeEngValues.delete(qualifiedName);
      } else {
        this.latestRealtimeEngValues.set(qualifiedName, engValue);
      }

      this.plotBuffer.addRealtimeValue(qualifiedName, time, rawValue, engValue);
    }
  }

  disconnect() {
    this.data$.complete();
    this.loading$.complete();
    this.realtimeSubscription?.cancel();
    this.syncSubscription?.unsubscribe();
  }

  private processSamples(samples: MeanSample[]): PlotPoint[] {
    const points: PlotPoint[] = [];
    for (const sample of samples) {
      points.push({
        time: Date.parse(sample.time),
        firstTime: Date.parse(sample.firstTime ?? sample.time),
        lastTime: Date.parse(sample.lastTime ?? sample.time),
        n: sample.n,
        avg: sample.avg ?? null,
        min: sample.min,
        max: sample.max,
      });
    }
    return points;
  }
}
