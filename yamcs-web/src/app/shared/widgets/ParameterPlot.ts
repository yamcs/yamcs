import { Component, ViewChild, AfterViewInit, ElementRef, Input } from '@angular/core';
import Dygraph from 'dygraphs';
import { Parameter } from '../../../yamcs-client';
import { YamcsService } from '../../core/services/YamcsService';
import { DyDataSource } from './DyDataSource';
import { DySample } from './DySample';

@Component({
  selector: 'app-parameter-plot',
  templateUrl: './ParameterPlot.html',
  styleUrls: ['./ParameterPlot.css'],
})
export class ParameterPlot implements AfterViewInit {

  @Input()
  parameter: Parameter;

  @ViewChild('graphContainer')
  graphContainer: ElementRef;

  dataSource: DyDataSource;

  dygraph: any;

  constructor(private yamcs: YamcsService) {
  }

  ngAfterViewInit() {
    const containingDiv = this.graphContainer.nativeElement as HTMLDivElement;
    const instanceClient = this.yamcs.getSelectedInstance();
    instanceClient.getParameterSamples(this.parameter.qualifiedName).subscribe(samples => {
      this.dataSource = new DyDataSource(samples);
      this.initDygraphs(containingDiv, this.dataSource.data);
    });
  }

  private initDygraphs(containingDiv: HTMLDivElement, samples: DySample[]) {
    const file = samples.length ? samples : 'X\n';
    this.dygraph = new Dygraph(containingDiv, file, {
      legend: 'always',
      drawGrid: true,
      drawPoints: true,
      showRoller: false,
      customBars: true,
      strokeWidth: 2,
      gridLineColor: '#444',
      axisLineColor: '#333',
      // axisLabelColor: '#666',
      axisLabelFontSize: 11,
      digitsAfterDecimal: 6,
      // panEdgeFraction: 0,
      labels: ['Generation Time', this.parameter.qualifiedName],
      labelsDiv: 'parameter-detail-legend',
      showRangeSelector: true,
      rangeSelectorPlotStrokeColor: '#333',
      rangeSelectorPlotFillColor: '#008080',
      /// valueRange: model.valueRange,
      yRangePad: 10,
      axes: {
          y: { axisLabelWidth: 50 }
      },
      rightGap: 0,
      labelsUTC: true,
      drawHighlightPointCallback: function(g: any, seriesName: any, ctx: any, cx: any, cy: any, color: any, radius: any) {
        ctx.beginPath();
        ctx.fillStyle = '#ffffff';
        ctx.arc(cx, cy, radius, 0, 2 * Math.PI, false);
        ctx.fill();
      },
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
