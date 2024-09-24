import { HistoricalDataProvider, NullablePoint, Widget } from '@yamcs/opi';
import { BackfillingSubscription, ConfigService, Synchronizer, YamcsService, utils } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { DyDataSource } from '../../../../shared/parameter-plot/DyDataSource';
import { PlotData } from '../../../../shared/parameter-plot/PlotBuffer';

export class OpiDisplayHistoricDataProvider implements HistoricalDataProvider {

  private processedSamples: NullablePoint[] = [];
  private dataSource: DyDataSource;

  private subscriptions: Subscription[] = [];
  private backfillSubscription?: BackfillingSubscription;

  constructor(
    pvName: string,
    widget: Widget,
    private yamcs: YamcsService,
    synchronizer: Synchronizer,
    configService: ConfigService,
  ) {
    this.dataSource = new DyDataSource(yamcs, synchronizer, configService);
    this.dataSource.extendRequestedRange = false;
    this.dataSource.resolution = 400;
    this.dataSource.addParameter({ qualifiedName: pvName });

    this.yamcs.range$.subscribe(range => {
      const stop = this.yamcs.getMissionTime();
      const start = utils.subtractDuration(stop, range);
      this.dataSource.updateWindow(start, stop, [null, null]);
    });

    this.dataSource.data$.subscribe(data => {
      this.processSamples(data);
      widget.requestRepaint();
    });

    // Autoscroll
    const sub = synchronizer.sync(() => {
      const stop = this.yamcs.getMissionTime();
      const start = utils.subtractDuration(stop, this.yamcs.range$.value);

      const startTime = start.getTime();
      for (let i = 0; i < this.processedSamples.length; i++) {
        const sample = this.processedSamples[i];
        if (sample.x >= startTime && i > 0) {
          this.processedSamples.splice(0, i);
          break;
        }
      }

      this.dataSource.updateWindowOnly(start, stop);
      this.addEdgeSamples(this.processedSamples);
      widget.requestRepaint();
    });
    this.subscriptions.push(sub);

    this.backfillSubscription = yamcs.yamcsClient.createBackfillingSubscription({
      instance: yamcs.instance!
    }, update => {
      if (update.finished) {
        this.dataSource.reloadVisibleRange();
      }
    });
  }

  private processSamples(data: PlotData) {
    const points: NullablePoint[] = [];
    for (const sample of data.samples) {
      const t = sample[0].getTime();
      if (sample[1]) {
        const avg = sample[1][1];
        points.push({ x: t, y: avg });
      } else {
        points.push({ x: t, y: null });
      }
    }

    this.addEdgeSamples(points);
    this.processedSamples = points;
  }

  private addEdgeSamples(points: NullablePoint[]) {
    const { visibleStart, visibleStop } = this.dataSource;
    if (visibleStart && visibleStop) {
      const visibleStartPoint: NullablePoint = {
        x: visibleStart.getTime(),
        y: null,
      };
      const visibleStopPoint: NullablePoint = {
        x: visibleStop.getTime(),
        y: null,
      };

      // Add null-points at the edges, so that the full request range
      // is visible
      if (points.length) {
        if (points[0].x > visibleStartPoint.x) {
          points.splice(0, 0, visibleStartPoint);
        }
        if (points[points.length - 1].x < visibleStopPoint.x) {
          points.push(visibleStopPoint);
        }
      } else {
        points.push(visibleStartPoint);
        points.push(visibleStopPoint);
      }
    }
  }

  getSamples(): NullablePoint[] {
    return this.processedSamples;
  }

  disconnect(): void {
    this.backfillSubscription?.cancel();
    this.subscriptions.forEach(x => x.unsubscribe());
    this.dataSource?.disconnect();
  }
}
