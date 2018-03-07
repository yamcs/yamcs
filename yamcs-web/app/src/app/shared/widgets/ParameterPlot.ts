import { Component, ViewChild, AfterViewInit, ElementRef, Input, QueryList, ContentChildren } from '@angular/core';
import Dygraph from 'dygraphs';
import { Parameter } from '../../../yamcs-client';
import { YamcsService } from '../../core/services/YamcsService';
import { DyDataSource } from './DyDataSource';
import { DySample } from './DySample';
import { ParameterSeries } from './ParameterSeries';

@Component({
  selector: 'app-parameter-plot',
  templateUrl: './ParameterPlot.html',
  styleUrls: ['./ParameterPlot.css'],
})
export class ParameterPlot implements AfterViewInit {

  @Input()
  showRangeSelector = false;

  @Input()
  xGrid = false;

  @Input()
  xAxis = true;

  @Input()
  xAxisLineWidth = 1;

  /**
   * Thickness of series
   */
  @Input()
  strokeWidth = 1;

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

  dataSource: DyDataSource;

  dygraph: any;

  private parameters: Parameter[] = [];

  constructor(private yamcs: YamcsService) {
  }

  ngAfterViewInit() {
    const containingDiv = this.graphContainer.nativeElement as HTMLDivElement;
    const instanceClient = this.yamcs.getSelectedInstance();

    // TODO should have endpoint on rest for multiple parameters sampled together
    this.parameters.push(this.seriesComponents.first.parameter);

    const stop = new Date();
    const start = new Date();
    start.setUTCHours(stop.getUTCHours() - 1);

    const qname = this.parameters[0].qualifiedName;
    instanceClient.getParameterSamples(qname, {
      start: start.toISOString(),
      stop: stop.toISOString(),
    }).subscribe(samples => {
      this.dataSource = new DyDataSource(samples);
      this.initDygraphs(containingDiv, this.dataSource.data);
    });
  }

  private initDygraphs(containingDiv: HTMLDivElement, samples: DySample[]) {
    const file = samples.length ? samples : 'X\n';
    const series: { [key: string]: any } = {};
    series[this.parameters[0].qualifiedName] = {
      color: '#000080',
    };
    this.dygraph = new Dygraph(containingDiv, file, {
      legend: 'always',
      drawGrid: false,
      drawPoints: false,
      showRoller: false,
      customBars: true,
      strokeWidth: this.strokeWidth,
      gridLineColor: '#d3d3d3',
      axisLineColor: '#aaa',
      axisLabelFontSize: 11,
      digitsAfterDecimal: 6,
      // panEdgeFraction: 0,
      labels: ['Generation Time', this.parameters[0].qualifiedName],
      labelsDiv: this.legend.nativeElement,
      showRangeSelector: this.showRangeSelector,
      rangeSelectorPlotStrokeColor: '#333',
      rangeSelectorPlotFillColor: '#008080',
      /// valueRange: model.valueRange,
      yRangePad: 10,
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
          includeZero: true,
        }
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
        ctx.stroke();
      },
      legendFormatter: (data: any) => {
        let legend = '';
        for (const trace of data.series) {
          legend += `${trace.dashHTML} ${trace.yHTML}`;
        }
        return legend;
      },
      rightGap: 0,
      labelsUTC: this.utc,
    });
  }

  colorForLevel(level: string) {
    switch (level) {
      case 'WATCH': return '#ffdddb';
      case 'WARNING': return '#ffc3c1';
      case 'DISTRESS': return '#ffaaa8';
      case 'CRITICAL': return '#c35e5c';
      case 'SEVERE': return '#a94442';
      default: console.error('Unknown level ' + level);
    }
  }
}
