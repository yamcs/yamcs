import { ConfigService, NamedObjectId, ParameterSubscription, ParameterValue, Sample, Synchronizer, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { NamedParameterType } from '../../shared/parameter-plot/NamedParameterType';
import { NamedSeries, PlotBuffer, PlotSeries } from './PlotBuffer';
import { PlotPoint } from './PlotPoint';

/**
 * Stores sample data for use in a ParameterPlot.
 */
export class PlotDataSource {

  // How many samples to load at once
  resolution = 6000;

  public loading$ = new BehaviorSubject<boolean>(false);

  data$ = new BehaviorSubject<PlotSeries[]>([]);

  visibleStart: Date;
  visibleStop: Date;

  parameters$ = new BehaviorSubject<NamedParameterType[]>([]);
  private plotBuffer: PlotBuffer;

  private lastLoadPromise: Promise<any> | null;

  private realtimeSubscription: ParameterSubscription;
  private syncSubscription: Subscription;
  private idMapping: { [key: number]: NamedObjectId; };

  constructor(
    private yamcs: YamcsService,
    synchronizer: Synchronizer,
    private configService: ConfigService,
  ) {
    this.syncSubscription = synchronizer.sync(() => {
      this.plotBuffer.forceNextPoint();
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
   */
  reloadVisibleRange() {
    return this.updateWindow(this.visibleStart, this.visibleStop);
  }

  updateWindowOnly(start: Date, stop: Date) {
    this.visibleStart = start;
    this.visibleStop = stop;
  }

  updateWindow(start: Date, stop: Date) {
    if (this.configService.getConfig().tmArchive) {
      this.loading$.next(true);
      // Load some offscreen data to reduce chances of being able to connect
      // an offscreen point with the start of the visible plot line.
      const offscreenEdge = (stop.getTime() - start.getTime()) / 10;
      const loadStart = new Date(start.getTime() - offscreenEdge);
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
          this.visibleStart = start;
          this.visibleStop = stop;
          const namedSeries: NamedSeries[] = [];
          for (let i = 0; i < results.length; i++) {
            const result = results[i];
            if (result.status === 'fulfilled') {
              namedSeries.push({
                name: parameters[i].qualifiedName,
                series: this.processSamples(result.value),
              });
            } else {
              console.warn(`Failed to retrieve samples for ${parameters[i].qualifiedName}`, result.reason);
              namedSeries.push({
                name: parameters[i].qualifiedName,
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
    } else {
      this.plotBuffer.reset();
      this.visibleStart = start;
      this.visibleStop = stop;
      // Even though there's no archive, pass series
      // information to PlotBuffer. It uses it to order
      // data when snapshotting.
      const namedSeries: NamedSeries[] = [];
      const parameters = this.parameters$.value;
      for (let i = 0; i < parameters.length; i++) {
        namedSeries.push({
          name: parameters[i].qualifiedName,
          series: [],
        });
      }
      this.plotBuffer.setArchiveData(namedSeries);
      // Quick emit, don't wait on sync tick
      this.emitDataUpdate();
    }
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

  private processRealtimeDelivery(pvals: ParameterValue[]) {
    for (const pval of pvals) {
      const id = this.idMapping[pval.numericId];
      const time = Date.parse(pval.generationTime);
      const value = utils.convertValueToNumber(pval.engValue);
      if (value === null || pval.acquisitionStatus !== 'ACQUIRED') {
        this.plotBuffer.addRealtimeValue(id.name, {
          time,
          firstTime: time,
          lastTime: time,
          n: 1,
          avg: null,
          min: null,
          max: null,
        });
      } else {
        this.plotBuffer.addRealtimeValue(id.name, {
          time,
          firstTime: time,
          lastTime: time,
          n: 1,
          avg: value,
          min: value,
          max: value,
        });
      }
    }
  }

  disconnect() {
    this.data$.complete();
    this.loading$.complete();
    this.realtimeSubscription?.cancel();
    this.syncSubscription?.unsubscribe();
  }

  private processSamples(samples: Sample[]): PlotPoint[] {
    const points: PlotPoint[] = [];
    for (const sample of samples) {
      points.push({
        time: Date.parse(sample.time),
        firstTime: Date.parse(sample.firstTime ?? sample.time),
        lastTime: Date.parse(sample.lastTime ?? sample.time),
        n: sample.n,
        avg: sample.avg,
        min: sample.min,
        max: sample.max,
      });
    }
    return points;
  }
}
