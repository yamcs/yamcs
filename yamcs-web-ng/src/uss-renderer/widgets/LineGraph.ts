import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
// import { Parameter } from '../Parameter';
import { Color } from '../Color';
import { G } from '../tags';

import Dygraph from 'dygraphs';

/**
 * TODO
 * - interaction model for y or x scroll
 * - interaction model for ctrl y or x zoom
 * - dynamically update xlabelwidth based on tick metrics
 * - day indication on domain title
 * - limit indication
 */
export class LineGraph extends AbstractWidget {

  graph: any;

  private title: string;
  private titleHeight: number;
  private graphBackgroundColor: Color;
  private plotBackgroundColor: Color;
  private xLabel: string;
  private yLabel: string;
  private xAxisOptions: any;
  private yAxisOptions: any;
  private xAxisColor: Color;
  private yAxisColor: Color;

  parseAndDraw() {
    this.title = utils.parseStringChild(this.node, 'Title');
    this.graphBackgroundColor = utils.parseColorChild(this.node, 'GraphBackgroundColor', Color.WHITE);
    this.plotBackgroundColor = utils.parseColorChild(this.node, 'PlotBackgroundColor', Color.WHITE);

    const fontFamily = 'sans-serif';
    let fontSize = 12;
    let color = '#000000';
    let underline = false;
    let italic = false;
    let bold = true;
    if (utils.hasChild(this.node, 'TitleTextStyle')) {
      const style = utils.findChild(this.node, 'TitleTextStyle');
      fontSize = utils.parseFloatChild(style, 'Fontsize');
      color = utils.parseColorChild(style, 'Color').toString();
      underline = utils.parseBooleanChild(style, 'IsUnderlined');
      italic = utils.parseBooleanChild(style, 'IsItalic');
      bold = utils.parseBooleanChild(style, 'IsBold');
    }
    let titleStyle = `font-family: ${fontFamily}; color: ${color}; font-size: ${fontSize}px`;
    titleStyle += (italic ? ';font-style: italic' : ';font-style: regular');
    titleStyle += (bold ? ';font-weight: bold' : ';font-weight: normal');
    titleStyle += (underline ? ';text-decoration: underline' : ';text-decoration: none');

    this.title = `<span style="${titleStyle}">${this.title}</span>`;
    this.titleHeight = fontSize;

    /*
     * X-AXIS (DOMAIN)
     */
    this.xAxisOptions = {
      axisLabelFontSize: 12,
      axisLabelWidth: 70,
      axisLabelFormatter: (d: Date, gran: any) => {
        const hh = d.getHours() < 9 ? '0' + d.getHours() : d.getHours();
        const mm = d.getMinutes() < 9 ? '0' + d.getMinutes() : d.getMinutes();
        const ss = d.getSeconds() < 9 ? '0' + d.getSeconds() : d.getSeconds();
        return `${hh}:${mm}:${ss}`;
      },
    };
    const domainGrid = utils.findChild(this.node, 'DomainGridlineDrawStyle');
    this.xAxisOptions['gridLineColor'] = utils.parseColorChild(domainGrid, 'Color');
    this.xAxisOptions['gridLineWidth'] = utils.parseFloatChild(domainGrid, 'Width');
    switch (utils.parseStringChild(domainGrid, 'Pattern')) {
      case 'DASHED':
        this.xAxisOptions['drawGrid'] = true;
        this.xAxisOptions['gridLinePattern'] = [4, 3];
        break;
      case 'DOTTED':
        this.xAxisOptions['drawGrid'] = true;
        this.xAxisOptions['gridLinePattern'] = [1, 5];
        break;
      case 'SOLID':
        this.xAxisOptions['drawGrid'] = true;
        break;
      case 'NONE':
        this.xAxisOptions['drawGrid'] = false;
        break;
    }
    const defaultDomainAxis = utils.findChild(this.node, 'DefaultDomainAxis');
    this.xLabel = utils.parseStringChild(defaultDomainAxis, 'Label');
    this.xAxisOptions['includeZero'] = utils.parseBooleanChild(defaultDomainAxis, 'StickyZero');
    if (!utils.parseBooleanChild(defaultDomainAxis, 'AutoRange')) {
      const axisRange = utils.findChild(defaultDomainAxis, 'AxisRange');
      this.xAxisOptions['valueRange'] = [
        utils.parseFloatChild(axisRange, 'Lower'),
        utils.parseFloatChild(axisRange, 'Upper'),
      ];
    }
    let xLabelStyle = 'font-family: sans-serif; font-weight: normal; font-size: 12px';
    this.xAxisColor = Color.BLACK;
    if (utils.hasChild(defaultDomainAxis, 'AxisColor')) {
      this.xAxisColor = utils.parseColorChild(defaultDomainAxis, 'AxisColor');
      this.xAxisOptions['axisLineColor'] = this.xAxisColor.toString();
      xLabelStyle += `;color: ${this.xAxisColor}`;
    }
    this.xLabel = `<span style="${xLabelStyle}">${this.xLabel}</span>`;

    /*
     * Y-AXIS (RANGE)
     */
    this.yAxisOptions = {
      axisLabelFontSize: 12,
      pixelsPerLabel: 12,
    };
    const rangeGrid = utils.findChild(this.node, 'RangeGridlineDrawStyle');
    this.yAxisOptions['gridLineColor'] = utils.parseColorChild(rangeGrid, 'Color');
    this.yAxisOptions['gridLineWidth'] = utils.parseFloatChild(rangeGrid, 'Width');
    switch (utils.parseStringChild(rangeGrid, 'Pattern')) {
      case 'DASHED':
        this.yAxisOptions['drawGrid'] = true;
        this.yAxisOptions['gridLinePattern'] = [4, 3];
        break;
      case 'DOTTED':
        this.yAxisOptions['drawGrid'] = true;
        this.yAxisOptions['gridLinePattern'] = [1, 5];
        break;
      case 'SOLID':
        this.yAxisOptions['drawGrid'] = true;
        break;
      case 'NONE':
        this.yAxisOptions['drawGrid'] = false;
        break;
    }
    const defaultRangeAxis = utils.findChild(this.node, 'DefaultRangeAxis');
    this.yLabel = utils.parseStringChild(defaultRangeAxis, 'Label');
    this.yAxisOptions['includeZero'] = utils.parseBooleanChild(defaultRangeAxis, 'StickyZero');
    if (!utils.parseBooleanChild(defaultRangeAxis, 'AutoRange')) {
      const axisRange = utils.findChild(defaultRangeAxis, 'AxisRange');
      this.yAxisOptions['valueRange'] = [
        utils.parseFloatChild(axisRange, 'Lower'),
        utils.parseFloatChild(axisRange, 'Upper'),
      ];
    }
    let yLabelStyle = 'font-family: sans-serif; font-weight: normal; font-size: 12px';
    this.yAxisColor = Color.BLACK;
    if (utils.hasChild(defaultRangeAxis, 'AxisColor')) {
      this.yAxisColor = utils.parseColorChild(defaultRangeAxis, 'AxisColor');
      this.yAxisOptions['axisLineColor'] = this.yAxisColor.toString();
      yLabelStyle += `;color: ${this.yAxisColor}`;
    }
    this.yLabel = `<span style="${yLabelStyle}">${this.yLabel}</span>`;

    return new G({
      id: this.id,
      class: 'line-graph',
      translate: `translate(${this.x},${this.y})`,
      'data-name': this.name,
    });
  }

  afterDomAttachment() {
    // First wrapper positions within the display
    // (LineGraphs are rendered outside of SVG)
    const container = document.createElement('div');
    const containerId = this.generateChildId();
    container.setAttribute('id', containerId);
    container.setAttribute('style', `position: absolute; left: ${this.x}px; top: ${this.y}px`);

    // Second wrapper because Dygraphs will modify its style attributes
    const graphWrapper = document.createElement('div');
    graphWrapper.setAttribute('style', `background-color: ${this.graphBackgroundColor}; border: 1px solid #a6a6a6`);
    container.appendChild(graphWrapper);

    // Some Dygraphs do not have a programmatic option. Use CSS instead
    const styleEl = document.createElement('style');
    styleEl.innerHTML = `
      #${containerId} .dygraph-axis-label-x { color: ${this.xAxisColor}; font-family: sans-serif; font-weight: 100 }
      #${containerId} .dygraph-axis-label-y { color: ${this.yAxisColor}; font-family: sans-serif; font-weight: 100 }
    `;
    container.appendChild(styleEl);

    this.display.container.appendChild(container);

    this.graph = new Dygraph(graphWrapper,
      `Date,A,B
      2016/01/01,10,20
      2016/07/01,20,10
      2016/12/31,40,30
      `, {
        title: this.title,
        titleHeight: this.titleHeight,
        fillGraph: true,
        interactionModel: {},
        width: this.width,
        height: this.height,
        xlabel: this.xLabel,
        ylabel: this.yLabel,
        axes: {
          x: this.xAxisOptions,
          y: this.yAxisOptions,
        },
        underlayCallback: (ctx: CanvasRenderingContext2D, area: any, g: any) => {
          ctx.globalAlpha = 1;

          // Colorize plot area
          ctx.fillStyle = this.plotBackgroundColor.toString();
          ctx.fillRect(area.x, area.y, area.w, area.h);

          // Add plot area contours
          ctx.strokeStyle = '#c0c0c0';

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
        },
      }
    );
  }

  /*updateValue(para: Parameter, usingRaw: boolean) {
    const series = this.chart.get('series-1');
    const value = this.getParameterValue(para, usingRaw);
    const t = para.generationTime;
    const xaxis = series.xAxis;
    if (!this.xAutoRange) {
      const extr = xaxis.getExtremes();
      if (extr.max < t) {
        const s = this.xRange / 3;
        xaxis.setExtremes(t + s - this.xRange, t + s);
      }
    }
    series.addPoint([t, value]);
  }*/
}
