import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { ElementRef } from '@angular/core';
import { Line, LinePlot } from '@fqqb/timeline';
import { BackfillingSubscription, ConfigService, Synchronizer, TimelineBand, YamcsService } from '@yamcs/webapp-sdk';
import { NamedParameterType } from '../../shared/parameter-plot/NamedParameterType';
import { BooleanProperty, ColorProperty, convertColor, NumberProperty, PropertyInfoSet, resolveProperties, TextProperty } from '../shared/properties';
import { TimelineChartComponent } from '../timeline-chart/timeline-chart.component';
import { ParameterPlotTooltipComponent } from './parameter-plot-tooltip/parameter-plot-tooltip.component';
import { PlotDataSource } from './PlotDataSource';

export const propertyInfo: PropertyInfoSet = {
  frozen: new BooleanProperty(false),
  height: new NumberProperty(30),
  minimum: new NumberProperty(),
  maximum: new NumberProperty(),
  zeroLineWidth: new NumberProperty(0),
  zeroLineColor: new ColorProperty('#ff0000'),
};

export function createTracePropertyInfo(index: number, color: string): PropertyInfoSet {
  const set: PropertyInfoSet = {};
  set[`trace_${index}_parameter`] = new TextProperty('');
  set[`trace_${index}_lineColor`] = new TextProperty(color);
  set[`trace_${index}_visible`] = new BooleanProperty(true);
  set[`trace_${index}_lineWidth`] = new NumberProperty(1);
  set[`trace_${index}_fill`] = new BooleanProperty(false);
  set[`trace_${index}_fillColor`] = new TextProperty('#dddddd');
  set[`trace_${index}_minMax`] = new BooleanProperty(false);
  set[`trace_${index}_minMaxOpacity`] = new NumberProperty(0.17);
  return set;
}

export function resolveTraceProperties(index: number, info: PropertyInfoSet, properties: { [key: string]: any; }) {
  const prefix = `trace_${index}_`;
  const prefixedResult = resolveProperties(info, properties);
  const lstripped: { [key: string]: any; } = {};
  for (const key in prefixedResult) {
    lstripped[key.slice(prefix.length)] = prefixedResult[key];
  }
  return lstripped;
}

export const DEFAULT_COLORS = [
  '#1b73e8',
  '#129eaf',
  '#d01984',
  '#34a853',
  '#7626bb',
  '#e64b19',
];

export class ParameterPlot extends LinePlot {

  private dataSource: PlotDataSource;

  private tooltipInstance: ParameterPlotTooltipComponent;
  private backfillSubscription?: BackfillingSubscription;

  constructor(
    chart: TimelineChartComponent,
    bandInfo: TimelineBand,
    yamcs: YamcsService,
    synchronizer: Synchronizer,
    configService: ConfigService,
    overlay: Overlay,
  ) {
    super(chart.timeline);
    this.label = bandInfo.name;
    this.data = { band: bandInfo };

    const properties = resolveProperties(propertyInfo, bandInfo.properties || {});
    this.frozen = properties.frozen ?? propertyInfo.frozen.defaultValue;
    this.contentHeight = properties.height ?? propertyInfo.height.defaultValue;
    this.labelBackground = 'rgba(255, 255, 255, 0.75)';
    this.minimum = properties.minimum ?? propertyInfo.minimum.defaultValue;
    this.maximum = properties.maximum ?? propertyInfo.maximum.defaultValue;
    this.zeroLineWidth = properties.zeroLineWidth ?? propertyInfo.zeroLineWidth.defaultValue;
    this.zeroLineColor = properties.zeroLineColor ?? propertyInfo.zeroLineColor.defaultValue;

    const bodyRef = new ElementRef(document.body);
    const positionStrategy = overlay.position().flexibleConnectedTo(bodyRef)
      .withPositions([{
        originX: 'start',
        originY: 'top',
        overlayX: 'start',
        overlayY: 'top',
      }]).withPush(false);

    const overlayRef = overlay.create({ positionStrategy });
    const tooltipPortal = new ComponentPortal(ParameterPlotTooltipComponent);
    this.tooltipInstance = overlayRef.attach(tooltipPortal).instance;

    const traces: Array<{ [key: string]: any; }> = [];

    let idx = 1;
    while (true) {
      const tracePropertyInfo = createTracePropertyInfo(idx, '#zzzzzz');
      const traceProperties = resolveTraceProperties(idx, tracePropertyInfo, bandInfo.properties || {});
      if (!traceProperties.parameter) {
        break;
      }
      idx++;
      traces.push(traceProperties);
    }

    this.dataSource = new PlotDataSource(yamcs, synchronizer, configService);
    this.dataSource.data$.subscribe(data => {
      const lines: Line[] = [];
      for (let i = 0; i < traces.length; i++) {
        const trace = traces[i];
        const series = data.series.length ? data.series[i] : [];
        const points = new Map<number, number | null>();
        const minMax = new Map<number, [number, number] | null>();
        for (let j = 0; j < series.length; j++) {
          const sample = series[j];
          if (sample.n === 0) {
            const time = Date.parse(sample.time);
            points.set(time, null);
            minMax.set(time, null);
          } else {
            const prevIsGap = j === 0 || series[j - 1].n === 0;
            const nextIsGap = (j === series.length - 1) || (series[j + 1].n === 0);

            let time: number;
            if (prevIsGap && !nextIsGap) {
              time = Date.parse(sample.firstTime);
            } else if (!prevIsGap && nextIsGap) {
              time = Date.parse(sample.lastTime);
            } else {
              time = Date.parse(sample.time);
            }

            points.set(time, sample.avg);
            minMax.set(time, [sample.min, sample.max]);
          }
        }
        const hexOpacity = Math.floor(trace.minMaxOpacity * 255).toString(16);
        lines.push({
          visible: trace.visible,
          points,
          pointRadius: 0,
          lohi: trace.minMax ? minMax : undefined,
          lineColor: trace.lineColor,
          lohiColor: convertColor(trace.lineColor) + hexOpacity,
          lineWidth: trace.lineWidth,
          fill: trace.fill ? trace.fillColor : 'transparent',
        });
      }
      this.lines = lines;
    });

    this.backfillSubscription = yamcs.yamcsClient.createBackfillingSubscription({
      instance: yamcs.instance!
    }, update => {
      if (update.finished) {
        this.dataSource.reloadVisibleRange();
      }
    });

    const toAdd: NamedParameterType[] = [];
    for (let i = 0; i < traces.length; i++) {
      toAdd.push({ qualifiedName: traces[i].parameter });
    }
    if (toAdd.length) {
      this.dataSource.addParameter(...toAdd);
    }

    this.addMouseMoveListener(evt => {
      this.tooltipInstance.show(evt.clientX, evt.clientY, new Date(evt.time), traces, evt.points);
    });
    this.addMouseLeaveListener(evt => {
      this.tooltipInstance.hide();
    });
  }

  refreshData() {
    // Load beyond the edges (for pan purposes)
    const viewportRange = 0; // this.timeline.stop - this.timeline.start;
    const loadStart = this.timeline.start - viewportRange;
    const loadStop = this.timeline.stop + viewportRange;
    this.dataSource.updateWindow(new Date(loadStart), new Date(loadStop), [null, null]);
  }

  override disconnectedCallback(): void {
    this.backfillSubscription?.cancel();
    this.dataSource.disconnect();
  }
}
