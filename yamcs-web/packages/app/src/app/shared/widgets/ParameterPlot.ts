import { Component, ViewChild, AfterViewInit, ElementRef, Input, QueryList, ContentChildren } from '@angular/core';
import Dygraph from 'dygraphs';
import { Parameter } from '@yamcs/client';
import { DyDataSource } from './DyDataSource';
import { ParameterSeries } from './ParameterSeries';
import GridPlugin from './GridPlugin';
import { subtractDuration } from '../utils';

@Component({
  selector: 'app-parameter-plot',
  templateUrl: './ParameterPlot.html',
  styleUrls: ['./ParameterPlot.css'],
})
export class ParameterPlot implements AfterViewInit {

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

  /**
   * Thickness of series
   */
  @Input()
  strokeWidth = 1;

  @Input()
  height = '100%';

  @Input()
  width = '100%';

  @Input()
  axisBackgroundColor = '#fff';

  /**
   * If true display timestamps in UTC, otherwise use browser default
   */
  @Input()
  utc = true;

  @ContentChildren(ParameterSeries)
  seriesComponents: QueryList<ParameterSeries>;

  @ViewChild('legend')
  legend: ElementRef;

  @ViewChild('graphContainer')
  graphContainer: ElementRef;

  dygraph: any;

  private parameters: Parameter[] = [];

  ngAfterViewInit() {
    const containingDiv = this.graphContainer.nativeElement as HTMLDivElement;

    // TODO should have endpoint on rest for multiple parameters sampled together
    this.parameters.push(this.seriesComponents.first.parameter);

    this.initDygraphs(containingDiv);
    this.dataSource.data$.subscribe(data => {
      const dyOptions: { [key: string]: any } = {
        file: data.samples.length ? data.samples : 'X\n',
      };
      if (this.dataSource.visibleStart) { // May be undefined on subject initial []
        dyOptions.dateWindow = [
          this.dataSource.visibleStart.getTime(),
          this.dataSource.visibleStop.getTime(),
        ];
      }
      if (data.restoreValueRange) {
        dyOptions.axes = {
          y: { valueRange: data.restoreValueRange }
        };
      } else {
        const valueRange = this.seriesComponents.first.getStaticValueRange();
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

    const now = new Date(); // TODO use mission time instead
    const start = subtractDuration(now, 'PT1H');

    // Add some padding to the right
    const delta = now.getTime() - start.getTime();
    const stop = new Date();
    stop.setTime(now.getTime() + 0.1 * delta);

    this.dataSource.setDateWindow(start, stop);
  }

  private initDygraphs(containingDiv: HTMLDivElement) {
    const series: { [key: string]: any } = {};
    series[this.parameters[0].qualifiedName] = {
      color: '#000080',
    };

    const alarmZones = this.seriesComponents.first.staticAlarmZones;
    const alarmRangeMode = this.seriesComponents.first.alarmRanges;

    let lastClickedGraph: any = null;

    const dyOptions: {[key: string]: any} = {
      legend: 'always',
      fillGraph: this.fillGraph,
      drawGrid: false,
      drawPoints: false,
      showRoller: false,
      customBars: true,
      strokeWidth: this.strokeWidth,
      gridLineColor: '#f2f2f2',
      axisLineColor: '#e1e1e1',
      axisLabelFontSize: 11,
      digitsAfterDecimal: 6,
      labels: ['Generation Time', this.parameters[0].qualifiedName],
      rightGap: 0,
      labelsUTC: this.utc,
      series,
      axes: {
        x: {
          drawAxis: this.xAxis,
          drawGrid: this.xGrid,
          axisLineWidth: this.xAxisLineWidth || 0.0000001, // Dygraphs does not handle 0 correctly
        },
        y: {
          axisLabelWidth: 50,
          drawAxis: this.seriesComponents.first.axis,
          drawGrid: this.seriesComponents.first.grid,
          axisLineWidth: this.seriesComponents.first.axisLineWidth || 0.0000001, // Dygraphs does not handle 0 correctly
          // includeZero: true,
          valueRange: this.seriesComponents.first.getStaticValueRange(),
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
            Dygraph.movePan(event, g, context);
          } else if (context.isZooming) {
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
          this.dataSource.setDateWindow(start, stop, yAxisRange);
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
            this.dataSource.setDateWindow(start, stop, yAxisRange);
          }
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
        ctx.fillStyle = '#fff';
        ctx.fillRect(area.x, area.y, area.w, area.h);

        // Colorize plot area
        if (alarmRangeMode === 'line') {
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
        } else if (alarmRangeMode === 'fill') {
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
      drawHighlightPointCallback: function (
        g: any,
        seriesName: string,
        ctx: CanvasRenderingContext2D,
        cx: number,
        cy: number,
        color: any,
        radius: number,
      ) {
        ctx.beginPath();
        ctx.arc(cx, cy, radius, 0, 2 * Math.PI, false);
        ctx.fill();

        ctx.fillStyle = '#ccc';
        ctx.moveTo(cx, cy);
        ctx.lineTo(cx + 10, cy + 10);
        /// ctx.stroke();
      },
      legendFormatter: (data: any) => {
        let legend = '';
        for (const trace of data.series) {
          legend += `${trace.dashHTML} ${trace.yHTML}`;
        }
        return legend;
      },
    };

    if (this.legend) {
      dyOptions.labelsDiv = this.legend.nativeElement;
    }
    if (this.xAxisHeight !== undefined) {
      dyOptions.xAxisHeight = this.xAxisHeight;
    }

    this.dygraph = new Dygraph(containingDiv, 'X\n', dyOptions);

    const gridPluginInstance = this.dygraph.getPluginInstance_(GridPlugin) as GridPlugin;
    gridPluginInstance.setAlarmZones(alarmZones);
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
    this.dataSource.setDateWindow(start, stop);
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
    this.dataSource.setDateWindow(start, stop);
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
}
