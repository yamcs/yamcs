import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { ElementRef } from '@angular/core';
import { Line, LinePlot, LinePoint, Timeline } from '@fqqb/timeline';
import {
  BackfillingSubscription,
  ConfigService,
  Synchronizer,
  utils,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { convertColor } from '../../../timeline/bands/properties';
import { Legend } from './Legend';
import { PlotData } from './PlotBuffer';
import { PlotDataSource } from './PlotDataSource';
import { TraceConfig } from './TraceConfig';
import { ParameterChartTooltipComponent } from './tooltip.component';

export class PlotBand extends LinePlot {
  private dataSource: PlotDataSource;

  private traceConfigById = new Map<string, TraceConfig>();
  private orderedTraceIds: string[] = [];

  private tooltipInstance: ParameterChartTooltipComponent;
  private tooltipOverlayRef?: OverlayRef;
  private backfillSubscription?: BackfillingSubscription;

  constructor(
    timeline: Timeline,
    yamcs: YamcsService,
    synchronizer: Synchronizer,
    configService: ConfigService,
    private overlay: Overlay,
    legend: Legend,
  ) {
    super(timeline);
    this.axisWidth = timeline.leftSidebar!.width;
    this.axisBackground = 'transparent';
    this.labelTextSize = 10;
    this.labelFontFamily = 'Roboto, sans-serif';
    this.lineWidth = 2;
    this.pointRadius = 0;
    this.resetAxisZoomOnDoubleClick = false;

    this.dataSource = new PlotDataSource(yamcs, synchronizer, configService);
    this.dataSource.data$.subscribe((data) => this.loadData(data));

    this.setupTooltip();

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

    this.addMouseMoveListener((evt) => {
      // Highlight closest hovered points
      let update = false;
      for (const line of this.lines) {
        for (const point of line.points) {
          if (point.pointRadius !== undefined) {
            point.pointRadius = undefined;
            update = true;
          }
        }
      }

      const trace2point = new Map<string, LinePoint>();
      // Should be of equal length, but check to be sure
      if (evt.points.length === this.lines.length) {
        for (let i = 0; i < evt.points.length; i++) {
          const traceId = this.lines[i].data!.traceId;
          const point = evt.points[i];
          if (point) {
            trace2point.set(traceId, point);
          }
        }
      }

      for (const point of evt.points) {
        if (point) {
          point.pointRadius = 4;
          update = true;
        }
      }
      if (update) {
        this.updatePlot();
      }

      // Update tooltip
      this.tooltipInstance.show(
        evt.clientX,
        evt.clientY,
        new Date(evt.time),
        legend,
        trace2point,
        this.hoveredValueLabelFormatter,
      );
      this.addMouseLeaveListener((evt) => {
        this.tooltipInstance.hide();
      });
    });
  }

  private setupTooltip() {
    const bodyRef = new ElementRef(document.body);
    const positionStrategy = this.overlay
      .position()
      .flexibleConnectedTo(bodyRef)
      .withPositions([
        {
          originX: 'start',
          originY: 'top',
          overlayX: 'start',
          overlayY: 'top',
        },
      ])
      .withPush(false);

    this.tooltipOverlayRef = this.overlay.create({ positionStrategy });
    const tooltipPortal = new ComponentPortal(ParameterChartTooltipComponent);
    this.tooltipInstance =
      this.tooltipOverlayRef.attach(tooltipPortal).instance;
  }

  updateWindow(fetch: boolean) {
    const loadStart = this.timeline.start;
    const loadStop = this.timeline.stop;
    this.dataSource.updateWindow(
      new Date(loadStart),
      new Date(loadStop),
      fetch,
    );
  }

  private loadData(data: PlotData[]) {
    const lines: Line[] = [];

    for (const traceId of this.orderedTraceIds) {
      const config = this.traceConfigById.get(traceId)!;
      let points: LinePoint[] = [];

      for (let i = 0; i < data.length; i++) {
        const traceData = data[i];
        if (traceData.traceId === traceId) {
          for (const point of traceData.points || []) {
            if (point.n === 0) {
              points.push({
                x: point.time,
                y: null,
              });
            } else {
              const prevIsGap = i === 0 || traceData.points[i - 1].n === 0;
              const nextIsGap =
                i === traceData.points.length - 1 ||
                traceData.points[i + 1].n === 0;

              let time: number;
              if (prevIsGap && !nextIsGap) {
                time = point.firstTime;
              } else if (!prevIsGap && nextIsGap) {
                time = point.lastTime;
              } else {
                time = point.time;
              }

              points.push({
                x: time,
                y: point.avg,
                low: point.min ?? undefined,
                high: point.max ?? undefined,
              });
            }
          }
          break;
        }
      }

      const hexOpacity = Math.floor(0.15 * 255).toString(16);
      lines.push({
        points,
        pointRadius: 0,
        pointColor: config.color,
        lineColor: config.color,
        lineWidth: config.lineWidth,
        lineStyle: config.lineStyle,
        lohiColor: convertColor(config.color) + hexOpacity,
        fill: config.fill
          ? this.createFillGradient(config.color)
          : 'transparent',
        data: {
          traceId,
          config,
        },
      });
    }

    this.lines = lines;
  }

  applyTraceConfigs() {
    // Maybe we have to reorder the lines
    const lineMap = new Map(
      this.lines.map((line) => [line.data?.traceId, line]),
    );
    const orderedLines = this.orderedTraceIds
      .map((id) => lineMap.get(id))
      .filter((line) => !!line);

    for (const line of orderedLines) {
      if (line.data?.config) {
        const config = line.data.config as TraceConfig;
        line.pointColor = config.color;
        line.lineColor = config.color;
        line.lineWidth = config.lineWidth;
        line.lineStyle = config.lineStyle;
        line.fill = config.fill
          ? this.createFillGradient(config.color)
          : 'transparent';
      }
    }

    this.lines = orderedLines;
  }

  toggleFill(enabled: boolean) {
    this.traceConfigById.forEach((config) => (config.fill = enabled));
    this.applyTraceConfigs();
  }

  onResize() {
    // Recalculate any gradients to match new height
    this.applyTraceConfigs();
  }

  getTrace(traceId: string) {
    return this.traceConfigById.get(traceId);
  }

  addOrUpdateTrace(traceId: string, config: TraceConfig) {
    if (this.orderedTraceIds.indexOf(traceId) === -1) {
      this.orderedTraceIds.push(traceId);
    }

    this.traceConfigById.set(traceId, config);
    this.dataSource.addOrUpdateTrace(traceId, config);

    // Apply order
    this.applyTraceConfigs();
  }

  removeTrace(traceId: string) {
    const idx = this.orderedTraceIds.indexOf(traceId);
    if (idx !== -1) {
      this.orderedTraceIds.splice(idx, 1);
    }
    this.traceConfigById.delete(traceId);
    this.dataSource.removeTrace(traceId);

    // Apply order
    this.applyTraceConfigs();
  }

  applyOrder(traceIds: string[]) {
    // Filter out traceIds that are not (yet) added to PlotBand
    // (for example: empty parameter field)
    this.orderedTraceIds = traceIds.filter(
      (id) => this.orderedTraceIds.indexOf(id) !== -1,
    );
    this.applyTraceConfigs();
  }

  getParameterValue(traceId: string) {
    const config = this.getTrace(traceId);
    if (config?.valueType === 'raw') {
      const qualifiedName = utils.getMemberPath(config.parameter)!;
      return this.dataSource.latestRealtimeRawValues.get(qualifiedName);
    } else if (config?.valueType === 'engineering') {
      const qualifiedName = utils.getMemberPath(config.parameter)!;
      return this.dataSource.latestRealtimeEngValues.get(qualifiedName);
    }
  }

  private createFillGradient(hexColor: string) {
    const offscreenCanvas = new OffscreenCanvas(1, 1); // Size can be minimal
    const offscreenCtx = offscreenCanvas.getContext('2d')!;
    const gradient = offscreenCtx.createLinearGradient(
      0,
      0,
      0,
      this.contentHeight,
    );
    gradient.addColorStop(0, `${hexColor}99`);
    gradient.addColorStop(1, `${hexColor}22`);
    return gradient;
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    this.tooltipOverlayRef?.dispose();
    this.backfillSubscription?.cancel();
    this.dataSource.disconnect();
  }
}
