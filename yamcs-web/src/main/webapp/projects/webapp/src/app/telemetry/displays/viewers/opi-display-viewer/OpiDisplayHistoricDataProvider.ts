import { HistoricalDataProvider, NullablePoint, Widget } from '@yamcs/opi';
import {
  BackfillingSubscription,
  ConfigService,
  Synchronizer,
  YamcsService,
  utils,
} from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { DyDataSource } from './DyDataSource';
import { DyPlotData } from './DyPlotBuffer';

export class OpiDisplayHistoricDataProvider implements HistoricalDataProvider {
  private processedSamples: NullablePoint[] = []; // Backing data
  private renderSamples: NullablePoint[] = []; // What the widget actually receives
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

    this.yamcs.range$.subscribe((range) => {
      const stop = this.yamcs.getMissionTime();
      const start = utils.subtractDuration(stop, range);
      this.dataSource.updateWindow(start, stop, [null, null]);
    });

    this.dataSource.data$.subscribe((data) => {
      this.processSamples(data);
      widget.requestRepaint();
    });

    // Autoscroll
    const sub = synchronizer.sync(() => {
      const stop = this.yamcs.getMissionTime();
      const start = utils.subtractDuration(stop, this.yamcs.range$.value);

      this.dataSource.updateWindowOnly(start, stop);
      this.rebuildRenderSamples();

      widget.requestRepaint();
    });

    this.subscriptions.push(sub);

    this.backfillSubscription = yamcs.yamcsClient.createBackfillingSubscription(
      {
        instance: yamcs.instance!,
      },
      (update) => {
        if (update.finished) {
          this.dataSource.reloadVisibleRange();
        }
      },
    );
  }

  private processSamples(data: DyPlotData) {
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

    this.processedSamples = points;
    this.rebuildRenderSamples();
  }

  private rebuildRenderSamples() {
    const { visibleStart, visibleStop } = this.dataSource;

    if (!visibleStart || !visibleStop) {
      this.renderSamples = this.processedSamples;
      return;
    }

    const startTime = visibleStart.getTime();
    const stopTime = visibleStop.getTime();

    const points: NullablePoint[] = [];

    for (const point of this.processedSamples) {
      if (point.x < startTime) {
        continue;
      }

      if (point.x > stopTime) {
        break;
      }

      points.push(point);
    }

    this.addEdgeSamples(points);
    this.renderSamples = points;
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
    return this.renderSamples;
  }

  disconnect(): void {
    this.backfillSubscription?.cancel();
    this.subscriptions.forEach((x) => x.unsubscribe());
    this.dataSource?.disconnect();
  }
}
