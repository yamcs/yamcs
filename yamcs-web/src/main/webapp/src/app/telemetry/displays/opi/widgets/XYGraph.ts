import Dygraph from 'dygraphs';
import { G, Rect } from '../../tags';
import { Color } from '../Color';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

interface AxisData {
  axisTitle: string;
  axisColor: Color;
  gridColor: Color;
  minimum: number;
  maximum: number;
  logScale: boolean;
  timeFormat: number;
  dashGridLine: boolean;
}

export class XYGraph extends AbstractWidget {

  private plotAreaBackgroundColor: Color;
  private showPlotAreaBorder: Boolean;

  private axisDataSet: AxisData[] = [];

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    const plotAreaBackgroundColorNode = utils.findChild(node, 'plot_area_background_color');
    this.plotAreaBackgroundColor = utils.parseColorChild(plotAreaBackgroundColorNode);
    this.showPlotAreaBorder = utils.parseBooleanChild(node, 'show_plot_area_border');

    const axisCount = utils.parseIntChild(node, 'axis_count');
    for (let i = 0; i < axisCount; i++) {
      const axisColorNode = utils.findChild(node, `axis_${i}_axis_color`);
      const gridColorNode = utils.findChild(node, `axis_${i}_grid_color`);
      this.axisDataSet.push({
        axisTitle: utils.parseStringChild(node, `axis_${i}_axis_title`),
        axisColor: utils.parseColorChild(axisColorNode),
        gridColor: utils.parseColorChild(gridColorNode),
        minimum: utils.parseFloatChild(node, `axis_${i}_minimum`),
        maximum: utils.parseFloatChild(node, `axis_${i}_maximum`),
        logScale: utils.parseBooleanChild(node, `axis_${i}_log_scale`),
        timeFormat: utils.parseIntChild(node, `axis_${i}_time_format`),
        dashGridLine: utils.parseBooleanChild(node, `axis_${i}_dash_grid_line`),
      });
    }
  }

  draw(g: G) {
    g.addChild(new Rect({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      fill: this.backgroundColor,
    }));
  }

  afterDomAttachment() {

    // First wrapper positions within the display
    const container = document.createElement('div');
    container.style.setProperty('position', 'absolute');
    container.style.setProperty('left', `${this.absoluteX + this.x}px`);
    container.style.setProperty('top', `${this.absoluteY + this.y}px`);
    container.style.setProperty('line-height', 'normal');

    // Second wrapper because Dygraphs will modify its style attributes
    const graphWrapper = document.createElement('div');
    graphWrapper.style.setProperty('background-color', this.backgroundColor.toString());
    graphWrapper.style.setProperty('box-sizing', 'border-box');
    graphWrapper.style.setProperty('border', '1px solid #a6a6a6');
    container.appendChild(graphWrapper);

    this.display.container.appendChild(container);

    /*
     * X-AXIS (DOMAIN)
     */
    const xAxis = this.axisDataSet[0];
    const xAxisOptions: {[key: string]: any} = {
      axisLineColor: xAxis.axisColor,
      gridLineColor: xAxis.gridColor,
      axisLabelFontSize: 12,
      axisLabelWidth: 70,
      drawGrid: true,
    };
    if (xAxis.dashGridLine) {
      xAxisOptions.gridLinePattern = [1, 5];
    }

    /*
     * Y-AXIS (RANGE)
     */
    const yAxis = this.axisDataSet[1];

    const series: { [key: string]: any } = {};
    series[yAxis.axisTitle] = {
      drawPoints: false,
      strokeWidth: 1,
      pointSize: 3,
    };

    const extraLabels: string[] = [];
    // TODO (from trace)

    const yAxisOptions: {[key: string]: any} = {
      axisLineColor: yAxis.axisColor,
      gridLineColor: yAxis.gridColor,
      axisLabelFontSize: 12,
      // pixelsPerLabel: 12,
      valueRange: [yAxis.minimum, yAxis.maximum],
      drawGrid: true,
    };
    if (yAxis.dashGridLine) {
      yAxisOptions.gridLinePattern = [1, 5];
    }

    const graph = new Dygraph(graphWrapper, 'X\n', {
      title: 'NYC vs. SF',
      fillGraph: false,
      interationModel: {},
      width: this.width,
      height: this.height,
      xlabel: xAxis.axisTitle,
      ylabel: yAxis.axisTitle,
      labels: [xAxis.axisTitle, yAxis.axisTitle, ...extraLabels],
      series,
      labelsUTC: true,
      axes: {
        x: xAxisOptions,
        y: yAxisOptions,
      },
      underlayCallback: (ctx: CanvasRenderingContext2D, area: any, g: any) => {
        ctx.globalAlpha = 1;

        // Colorize plot area
        ctx.fillStyle = this.plotAreaBackgroundColor.toString();
        ctx.fillRect(area.x, area.y, area.w, area.h);

        if (this.showPlotAreaBorder) {
          // Add plot area contours
          ctx.strokeStyle = '#000';

          // Plot Area Top
          ctx.beginPath();
          ctx.moveTo(area.x, area.y);
          ctx.lineTo(area.x + area.w, area.y);
          ctx.stroke();

          // Plot Area Right
          ctx.beginPath();
          ctx.moveTo(area.x + area.w, area.y);
          ctx.lineTo(area.x + area.w, area.y + area.h);
          ctx.stroke();
        }
      }
    });
  }
}
