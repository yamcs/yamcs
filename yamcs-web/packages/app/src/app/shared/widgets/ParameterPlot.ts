import { AfterViewInit, Component, ContentChildren, ElementRef, Input, OnDestroy, QueryList, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material';
import { Parameter } from '@yamcs/client';
import Dygraph from 'dygraphs';
import { BehaviorSubject, Subscription } from 'rxjs';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { ModifyParameterDialog } from '../../mdb/parameters/ModifyParameterDialog';
import { subtractDuration } from '../utils';
import CrosshairPlugin from './CrosshairPlugin';
import { DyDataSource } from './DyDataSource';
import { analyzeStaticValueRanges, DyLegendData, TimestampTrackerData } from './dygraphs';
import GridPlugin from './GridPlugin';
import { ParameterSeries } from './ParameterSeries';

@Component({
  selector: 'app-parameter-plot',
  templateUrl: './ParameterPlot.html',
  styleUrls: ['./ParameterPlot.css'],
})
export class ParameterPlot implements AfterViewInit, OnDestroy {

  @Input()
  dataSource: DyDataSource;

  @Input()
  fillGraph = false;

  @Input()
  xGrid = false;

  @Input()
  xAxis = true;

  @Input()
  xAxisLineWidth = 1;

  @Input()
  xAxisHeight: number;

  @Input()
  duration = 'PT1H';

  @Input()
  height = '100%';

  @Input()
  width = '100%';

  @Input() lightAxisBackgroundColor = '#fafafa';
  @Input() lightAxisLineColor = '#e1e1e1';
  @Input() lightGridLineColor = '#f2f2f2';
  @Input() lightPlotAreaBackgroundColor = '#fff';
  @Input() lightHighlightColor = '#e1e1e1';

  @Input() darkAxisBackgroundColor = '#191919';
  @Input() darkAxisLineColor = '#2e2e2e';
  @Input() darkGridLineColor = '#212121';
  @Input() darkPlotAreaBackgroundColor = '#111';
  @Input() darkHighlightColor = '#2e2e2e';

  axisBackgroundColor = this.lightAxisBackgroundColor;
  axisLineColor = this.lightAxisLineColor;
  gridLineColor = this.lightGridLineColor;
  plotAreaBackgroundColor = this.lightPlotAreaBackgroundColor;
  highlightColor = this.lightHighlightColor;

  @Input()
  removableSeries = false;

  @Input()
  stop = new Date();

  /**
   * If true display timestamps in UTC, otherwise use browser default
   */
  @Input()
  utc = true;

  @Input()
  crosshair: 'horizontal' | 'vertical' | 'both' | 'none' = 'vertical';

  @ContentChildren(ParameterSeries)
  seriesComponents: QueryList<ParameterSeries>;

  @ViewChild('graphContainer')
  graphContainer: ElementRef;

  dygraph: any;

  private parameters: Parameter[] = [];
  private parameterConfig = new Map<string, ParameterSeries>();

  // Flag to prevent from reloading while the user is busy with a pan or zoom operation
  private disableDataReload = false;

  private darkModeSubscription: Subscription;

  legendData$ = new BehaviorSubject<DyLegendData | null>(null);
  timestampTrackerData$ = new BehaviorSubject<TimestampTrackerData | null>(null);

  constructor(private preferenceStore: PreferenceStore, private dialog: MatDialog) {
    this.darkModeSubscription = preferenceStore.darkMode$.subscribe(darkMode => {
      this.applyTheme(darkMode);
    });
  }

  ngAfterViewInit() {
    this.dataSource.parameters$.value.forEach(p => {
      this.parameters.push(p);
    });

    this.seriesComponents.forEach(series => {
      this.parameterConfig.set(series.parameter, series);
    });

    const containingDiv = this.graphContainer.nativeElement as HTMLDivElement;

    this.initDygraphs(containingDiv);
    this.dataSource.data$.subscribe(data => {
      if (this.disableDataReload) {
        return;
      }

      const dyOptions: { [key: string]: any } = {
        file: data.samples.length ? data.samples : 'X\n',
      };
      if (this.dataSource.visibleStart) { // May be undefined on subject initial []
        dyOptions.dateWindow = [
          this.dataSource.visibleStart.getTime(),
          this.dataSource.visibleStop.getTime(),
        ];
      }
      if (data.valueRange[0] !== null && data.valueRange[1] !== null) {
        dyOptions.axes = {
          y: { valueRange: data.valueRange }
        };
      } else {
        const valueRange = analyzeStaticValueRanges(this.parameters[0]).valueRange;
        let lo = valueRange[0];
        if (this.dataSource.minValue !== undefined) {
          lo = (lo !== null) ? Math.min(lo, this.dataSource.minValue) : this.dataSource.minValue;
        }
        let hi = valueRange[1];
        if (this.dataSource.maxValue !== undefined) {
          hi = (hi !== null) ? Math.max(hi, this.dataSource.maxValue) : this.dataSource.maxValue;
        }

        // Prevent identical lo/hi
        if (lo === hi && lo !== null) {
          lo = Math.min(lo, 0);
          hi = Math.max(hi!, 0);
        }

        // Add extra y padding for visual comfort
        if (lo !== null && hi !== null) {
          lo = lo - (hi - lo) * 0.1;
          hi = hi + (hi - lo) * 0.1;
        }

        dyOptions.axes = {
          y: { valueRange: [lo, hi] }
        };
      }
      this.dygraph.updateOptions(dyOptions);
      this.dygraph.setAnnotations(data.annotations);
    });

    /*
     * Trigger initial load
     */
    const stop = this.stop;
    const start = subtractDuration(stop, this.duration);

    // Add some padding to the right
    const delta = stop.getTime() - start.getTime();
    stop.setTime(stop.getTime() + 0.1 * delta);

    this.dataSource.updateWindow(start, stop, [null, null]);
    this.applyTheme(this.preferenceStore.isDarkMode());
  }

  private initDygraphs(containingDiv: HTMLDivElement) {
    const seriesByLabel: { [key: string]: any } = {};

    const configs = this.parameters.map(p => this.parameterConfig.get(p.qualifiedName)!);
    configs.forEach(config => {
      seriesByLabel[config.label || config.parameter] = {
        color: config.color,
        strokeWidth: config.strokeWidth,
      };
    });

    const primaryParameter = this.parameters[0];
    const primaryConfig = configs[0];
    const rangeAnalysis = analyzeStaticValueRanges(primaryParameter);
    const alarmZones = rangeAnalysis.staticAlarmZones;

    let lastClickedGraph: any = null;

    const dyOptions: { [key: string]: any } = {
      legend: 'always',
      fillGraph: this.fillGraph,
      drawGrid: false,
      drawPoints: false,
      showRoller: false,
      customBars: true,
      gridLineColor: this.gridLineColor,
      axisLineColor: this.axisLineColor,
      axisLabelFontSize: 11,
      digitsAfterDecimal: 6,
      labels: ['Generation Time', ...configs.map(s => s.label || s.parameter)],
      rightGap: 0,
      labelsUTC: this.utc,
      series: seriesByLabel,
      axes: {
        x: {
          drawAxis: this.xAxis,
          drawGrid: this.xGrid,
          axisLineWidth: this.xAxisLineWidth || 0.0000001, // Dygraphs does not handle 0 correctly
        },
        y: {
          axisLabelWidth: 50,
          drawAxis: primaryConfig.axis,
          drawGrid: primaryConfig.grid,
          axisLineWidth: primaryConfig.axisLineWidth || 0.0000001, // Dygraphs does not handle 0 correctly
          // includeZero: true,
          valueRange: analyzeStaticValueRanges(primaryParameter).valueRange,
        }
      },
      interactionModel: {
        mousedown: (event: any, g: any, context: any) => {
          context.initializeMouseDown(event, g, context);
          if (event.altKey || event.shiftKey) {
            Dygraph.startZoom(event, g, context);
          } else {
            Dygraph.startPan(event, g, context);
          }
        },
        mousemove: (event: any, g: any, context: any) => {
          if (context.isPanning) {
            this.disableDataReload = true;
            Dygraph.movePan(event, g, context);
          } else if (context.isZooming) {
            this.disableDataReload = true;
            Dygraph.moveZoom(event, g, context);
          }
        },
        mouseup: (event: any, g: any, context: any) => {
          if (context.isPanning) {
            Dygraph.endPan(event, g, context);
          } else if (context.isZooming) {
            Dygraph.endZoom(event, g, context);
          }

          const xAxisRange = g.xAxisRange();
          const start = new Date(xAxisRange[0]);
          const stop = new Date(xAxisRange[1]);

          const yAxisRange = g.yAxisRanges()[0];
          this.dataSource.updateWindow(start, stop, yAxisRange);

          this.disableDataReload = false;
        },
        click: (event: any, g: any, context: any) => {
          lastClickedGraph = g;
          event.preventDefault();
          event.stopPropagation();
        },
        /*dblclick: (event: any, g: any, context: any) => {
          // Reducing by 20% makes it 80% the original size, which means
          // to restore to original size it must grow by 25%

          if (!(event.offsetX && event.offsetY)) {
            event.offsetX = event.layerX - event.target.offsetLeft;
            event.offsetY = event.layerY - event.target.offsetTop;
          }

          const xPct = this.offsetToPercentage(event.offsetX);
          if (event.ctrlKey) {
            this.zoom(-.25, xPct);
          } else {
            this.zoom(.2, xPct);
          }
        },*/
        mouseout: (event: any, g: any, context: any) => {
          if (context.isPanning) {
            const xAxisRange = g.xAxisRange();
            const start = new Date(xAxisRange[0]);
            const stop = new Date(xAxisRange[1]);

            const yAxisRange = g.yAxisRanges()[0];
            this.dataSource.updateWindow(start, stop, yAxisRange);
          }
          this.disableDataReload = false;
        },
        mousewheel: (event: any, g: any, context: any) => {
          if (lastClickedGraph !== g) {
            return;
          }
          const normal = event.detail ? event.detail * -1 : event.wheelDelta / 40;
          // For me the normalized value shows 0.075 for one click. If I took
          // that verbatim, it would be a 7.5%.
          const percentage = normal / 50;

          if (!(event.offsetX && event.offsetY)) {
            event.offsetX = event.layerX - event.target.offsetLeft;
            event.offsetY = event.layerY - event.target.offsetTop;
          }

          const xPct = this.offsetToPercentage(event.offsetX);
          this.zoom(percentage, xPct);
          event.preventDefault();
          event.stopPropagation();
        },
      },
      underlayCallback: (ctx: CanvasRenderingContext2D, area: any, g: any) => {
        ctx.save();

        ctx.globalAlpha = 1;
        ctx.fillStyle = this.plotAreaBackgroundColor;
        ctx.fillRect(area.x, area.y, area.w, area.h);

        // Colorize plot area
        if (primaryConfig.alarmRanges === 'line') {
          for (const zone of alarmZones) {
            const zoneY = zone.y1IsLimit ? zone.y1 : zone.y2;
            const y = g.toDomCoords(0, zoneY)[1];

            ctx.strokeStyle = zone.color;
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(area.x, y);
            ctx.lineTo(area.x + area.w, y);
            ctx.stroke();
          }
        } else if (primaryConfig.alarmRanges === 'fill') {
          ctx.globalAlpha = 0.2;
          for (const zone of alarmZones) {
            if (zone.y2 === null) {
              return;
            }

            let y1, y2;
            if (zone.y1 === -Infinity) {
              y1 = area.y + area.h;
            } else {
              y1 = g.toDomCoords(0, zone.y1)[1];
            }

            if (zone.y2 === Infinity) {
              y2 = 0;
            } else {
              y2 = g.toDomCoords(0, zone.y2)[1];
            }

            ctx.fillStyle = zone.color;
            ctx.fillRect(
              area.x, Math.min(y1, y2),
              area.w, Math.abs(y2 - y1)
            );
          }
        }
        ctx.restore();
      },
      drawHighlightPointCallback: (
        g: any,
        seriesName: string,
        ctx: CanvasRenderingContext2D,
        cx: number,
        cy: number,
        color: any,
        radius: number,
      ) => {
        // Only draw for point of first series, because otherwise the line may
        // get drawn on top of other points.
        if ((primaryConfig.label || primaryConfig.parameter) === seriesName) {
          ctx.save();
          // ctx.clearRect(0, 0, g.width_, g.height_);
          ctx.setLineDash([5, 5]);
          ctx.strokeStyle = this.highlightColor;
          ctx.lineWidth = 1;

          this.timestampTrackerData$.next({
            timestamp: new Date(g.selPoints_[0].xval),
            canvasx: g.selPoints_[0].canvasx,
          });

          ctx.beginPath();
          const canvasx = Math.floor(g.selPoints_[0].canvasx) + 0.5; // crisper rendering
          if (this.crosshair === 'vertical' || this.crosshair === 'both') {
            ctx.moveTo(canvasx, 0);
            ctx.lineTo(canvasx, g.height_);
          }
          if (this.crosshair === 'horizontal' || this.crosshair === 'both') {
            for (const point of g.selPoints_) {
              const canvasy = Math.floor(point.canvasy) + 0.5; // crisper rendering
              ctx.moveTo(0, canvasy);
              ctx.lineTo(g.width_, canvasy);
            }
          }
          ctx.stroke();
          ctx.closePath();
          ctx.restore();
        }

        ctx.beginPath();
        ctx.arc(cx, cy, radius, 0, 2 * Math.PI, false);
        ctx.fill();
      },
      legendFormatter: (data: DyLegendData) => {
        for (const trace of data.series) {
          if (trace.y === undefined) {
            const rtValue = this.dataSource.latestRealtimeValues.get(trace.label);
            if (rtValue) {
              trace.y = rtValue[1];
              trace.yHTML = String(rtValue[1]);
            }
          }
        }
        this.legendData$.next(data);
        return '';
      },
      plugins: [
        new CrosshairPlugin(),
      ],
    };

    if (this.xAxisHeight !== undefined) {
      dyOptions.xAxisHeight = this.xAxisHeight;
    }

    // Install customized GridPlugin in global Dygraph object.
    Dygraph.Plugins['Grid'] = GridPlugin;
    Dygraph.PLUGINS = [
      Dygraph.Plugins['Legend'],
      Dygraph.Plugins['Axes'],
      Dygraph.Plugins['Annotations'],
      Dygraph.Plugins['ChartLabels'],
      Dygraph.Plugins['Grid'],
      Dygraph.Plugins['RangeSelector'],
    ];

    this.dygraph = new Dygraph(containingDiv, 'X\n', dyOptions);

    const gridPluginInstance = this.dygraph.getPluginInstance_(GridPlugin) as GridPlugin;
    gridPluginInstance.setAlarmZones(alarmZones);
  }

  getParameters() {
    return this.parameters;
  }

  addParameter(parameter: Parameter, parameterConfig: ParameterSeries) {
    this.dataSource.addParameter(parameter);
    this.parameters.push(parameter);
    this.parameterConfig.set(parameter.qualifiedName, parameterConfig);

    this.updateDygraphSeries();
  }

  removeParameter(label: string) {
    const qualifiedName = label; // TODO
    this.dataSource.removeParameter(qualifiedName);
    this.parameters = this.parameters.filter(p => p.qualifiedName !== qualifiedName);
    this.parameterConfig.delete(qualifiedName);

    this.updateDygraphSeries();
  }

  modifyParameter(label: string) {
    const props = this.dygraph.getPropertiesForSeries(label);
    const dialogRef = this.dialog.open(ModifyParameterDialog, {
      data: {
        ...props,
        strokeWidth: this.dygraph.getOption('strokeWidth', label),
      },
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        const config = this.parameterConfig.get(label)!;
        config.color = result.color;
        config.strokeWidth = result.thickness;
        this.updateDygraphSeries();
      }
    });
  }

  private updateDygraphSeries() {
    const seriesByLabel: { [key: string]: any } = {};

    const configs = this.parameters.map(p => this.parameterConfig.get(p.qualifiedName)!);
    configs.forEach(config => {
      seriesByLabel[config.label || config.parameter] = {
        color: config.color,
        strokeWidth: config.strokeWidth,
      };
    });

    const dyOptions: { [key: string]: any } = {
      labels: ['Generation Time', ...configs.map(s => s.label || s.parameter)],
      series: seriesByLabel,
    };

    // TODO when removing a series we get legend-related errors on hovering (before data is set)
    // Visually it works though.
    this.dataSource.reloadVisibleRange().then((() => {
      this.dygraph.updateOptions(dyOptions, true /* block redraw */);
    }));
  }

  private applyTheme(dark: boolean) {
    // Update model
    if (dark) {
      this.axisBackgroundColor = this.darkAxisBackgroundColor;
      this.axisLineColor = this.darkAxisLineColor;
      this.gridLineColor = this.darkGridLineColor;
      this.plotAreaBackgroundColor = this.darkPlotAreaBackgroundColor;
      this.highlightColor = this.darkHighlightColor;
    } else {
      this.axisBackgroundColor = this.lightAxisBackgroundColor;
      this.axisLineColor = this.lightAxisLineColor;
      this.gridLineColor = this.lightGridLineColor;
      this.plotAreaBackgroundColor = this.lightPlotAreaBackgroundColor;
      this.highlightColor = this.lightHighlightColor;
    }

    // Apply model
    if (this.dygraph) {
      this.dygraph.updateOptions({
        axisLineColor: this.axisLineColor,
        gridLineColor: this.gridLineColor,
      });
    }

    if (this.graphContainer) {
      const container = this.graphContainer.nativeElement as HTMLDivElement;
      container.style.backgroundColor = this.axisBackgroundColor;
    }
  }

  public getDateRange() {
    if (this.dygraph) {
      const range = this.dygraph.xAxisRange();
      return [new Date(range[0]), new Date(range[1])];
    }
  }

  public zoomIn() {
    this.zoom(0.2);
  }

  public zoomOut() {
    this.zoom(-0.25);
  }

  public reset() {
    const xAxisRange = this.dygraph.xAxisRange();
    const start = new Date(xAxisRange[0]);
    const stop = new Date(xAxisRange[1]);
    this.dataSource.updateWindow(start, stop, [null, null]);
  }

  /**
   *  Adjusts x by zoomInPercentage
   */
  zoom(zoomInPercentage: number, xBias = 0.5) {
    this.dygraph.updateOptions({
      dateWindow: this.adjustAxis(this.dygraph.xAxisRange(), zoomInPercentage, xBias),
    });

    const xAxisRange = this.dygraph.xAxisRange();
    const start = new Date(xAxisRange[0]);
    const stop = new Date(xAxisRange[1]);
    this.dataSource.updateWindow(start, stop, [null, null]);
  }

  private adjustAxis(axis: any, zoomInPercentage: number, bias: number) {
    const delta = axis[1] - axis[0];
    const increment = delta * zoomInPercentage;
    const foo = [increment * bias, increment * (1 - bias)];
    return [axis[0] + foo[0], axis[1] - foo[1]];
  }

  // Take the offset of a mouse event on the dygraph canvas and
  // convert it to a pair of percentages from the bottom left.
  private offsetToPercentage(offsetX: number) {
    // Calculate pixel offset of the leftmost date.
    const xOffset = this.dygraph.toDomCoords(this.dygraph.xAxisRange()[0], null)[0];

    // x y w and h are relative to the corner of the drawing area,
    // so that the upper corner of the drawing area is (0, 0).
    const x = offsetX - xOffset;

    // Calcuate the rightmost pixel, effectively defining the width
    const w = this.dygraph.toDomCoords(this.dygraph.xAxisRange()[1], null)[0] - xOffset;

    // Percentage from the left.
    return w === 0 ? 0 : (x / w);
  }

  ngOnDestroy() {
    if (this.darkModeSubscription) {
      this.darkModeSubscription.unsubscribe();
    }
  }
}
