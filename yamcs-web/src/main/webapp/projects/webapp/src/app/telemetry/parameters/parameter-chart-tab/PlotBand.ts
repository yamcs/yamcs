import { Line, LinePlot, LinePoint, Timeline } from '@fqqb/timeline';
import { utils } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { convertColor } from '../../../timeline/bands/properties';
import { Legend } from './Legend';
import { PlotData } from './PlotBuffer';
import { PlotDataSource } from './PlotDataSource';
import { TraceConfig } from './TraceConfig';
import { ParameterChartTooltipComponent } from './tooltip.component';

/**
 * One plot area of the chart.
 *
 * A band renders a subset of the chart's traces (all of them in overlay
 * layout, exactly one in stacked layout). It owns no data: the data source,
 * the tooltip and the legend are shared singletons owned by
 * ParameterChartTabComponent, so that bands can be cheaply destroyed and
 * recreated whenever the layout or the trace order changes.
 */
export class PlotBand extends LinePlot {
  private traceConfigById = new Map<string, TraceConfig>();
  private orderedTraceIds: string[] = [];

  private latestData: PlotData[] = [];
  private dataSubscription: Subscription;

  constructor(
    timeline: Timeline,
    private dataSource: PlotDataSource,
    tooltip: ParameterChartTooltipComponent,
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

    this.dataSubscription = dataSource.data$.subscribe((data) => {
      this.latestData = data;
      this.loadData(data);
    });

    this.addMouseLeaveListener(() => tooltip.hide());

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

      // Update tooltip. Only the traces of this band are listed, so that a
      // stacked layout does not show empty rows for the other bands.
      tooltip.show(
        evt.clientX,
        evt.clientY,
        new Date(evt.time),
        legend,
        trace2point,
        this.hoveredValueLabelFormatter,
        this.orderedTraceIds,
      );
    });
  }

  /**
   * Assigns the traces rendered by this band. The configs map is owned by the
   * component and may hold traces belonging to other bands; only the provided
   * ids are rendered.
   */
  setTraces(traceIds: string[], configById: Map<string, TraceConfig>) {
    this.orderedTraceIds = traceIds.filter((id) => configById.has(id));
    this.traceConfigById = new Map();
    for (const traceId of this.orderedTraceIds) {
      this.traceConfigById.set(traceId, configById.get(traceId)!);
    }
    this.loadData(this.latestData);
  }

  getTraceIds() {
    return [...this.orderedTraceIds];
  }

  hasTrace(traceId: string) {
    return this.traceConfigById.has(traceId);
  }

  private loadData(data: PlotData[]) {
    const lines: Line[] = [];

    for (const traceId of this.orderedTraceIds) {
      const config = this.traceConfigById.get(traceId)!;
      let points: LinePoint[] = [];

      for (let i = 0; i < data.length; i++) {
        const traceData = data[i];
        if (traceData.traceId === traceId) {
          const samples = traceData.points || [];
          for (let j = 0; j < samples.length; j++) {
            const point = samples[j];
            if (point.n === 0) {
              points.push({
                x: point.time,
                y: null,
              });
            } else {
              const prevIsGap = j === 0 || samples[j - 1].n === 0;
              const nextIsGap =
                j === samples.length - 1 || samples[j + 1].n === 0;

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
    this.updateValueFormatters();
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

    this.updateValueFormatters();
  }

  /**
   * Sets axis / tooltip formatters so enum traces render labels instead of raw
   * ordinals. Labels from every enum trace on the band are merged into one
   * ordinal→label table (last write wins on a collision). Resets to the plain
   * numeric formatting when no enum trace is present.
   *
   * In a stacked layout a band holds a single trace, so its axis shows exactly
   * that parameter's enumeration.
   */
  private updateValueFormatters() {
    const labelByOrdinal = new Map<number, string>();
    const configs = [...this.traceConfigById.values()];
    for (const config of configs) {
      if (config.valueType === 'engineering') {
        for (const ev of config.enumValues ?? []) {
          labelByOrdinal.set(ev.value, ev.label);
        }
      }
    }

    // Only when every trace on this band is categorical may the axis suppress
    // fractional ticks. A band mixing an enum with a numeric trace (possible in
    // overlay layout) still needs plain numbers for the numeric scale.
    const allCategorical =
      configs.length > 0 &&
      configs.every(
        (config) =>
          config.valueType === 'engineering' && !!config.enumValues?.length,
      );

    if (labelByOrdinal.size > 0) {
      const fmt = (value: number) =>
        Number.isInteger(value) && labelByOrdinal.has(value)
          ? labelByOrdinal.get(value)!
          : String(value);
      // Auto-generated ticks land on half-ordinals (1.5), which name no state.
      this.axisLabelFormatter = (value) =>
        allCategorical && !Number.isInteger(value) ? '' : fmt(value);
      this.hoveredValueLabelFormatter = fmt;
    } else {
      // Library defaults.
      this.axisLabelFormatter = (value) => String(value);
      this.hoveredValueLabelFormatter = (value) => value.toFixed(2);
    }
  }

  /**
   * Formats a realtime value for a single trace, mapping enum ordinals to their
   * label. Used by the legend.
   */
  getValueLabel(traceId: string, value: number): string {
    const config = this.getTrace(traceId);
    if (config?.valueType === 'engineering') {
      const ev = config.enumValues?.find((e) => e.value === value);
      if (ev) {
        return ev.label;
      }
    }
    return String(value);
  }

  onResize() {
    // Recalculate any gradients to match new height
    this.applyTraceConfigs();
  }

  getTrace(traceId: string) {
    return this.traceConfigById.get(traceId);
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
    this.dataSubscription?.unsubscribe();
  }
}
