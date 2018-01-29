import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Color } from '../Color';
import { G } from '../tags';

import Dygraph from 'dygraphs';
import { DataSourceSample } from '../DataSourceSample';
import { SampleBuffer, Sample } from '../SampleBuffer';
import { CircularBuffer } from '../CircularBuffer';
import { ExpirationBuffer } from '../ExpirationBuffer';
import { DataSourceBinding } from '../DataSourceBinding';

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * TODO
 * - interaction model for y or x scroll
 * - interaction model for ctrl y or x zoom
 * - dynamically update xlabelwidth based on tick metrics
 * - day indication on domain title
 * - limit indication
 */
export class LineGraph extends AbstractWidget {

  private graph: any;

  private utc = true;

  private title: string;
  private titleHeight: number;
  private graphBackgroundColor: Color;
  private plotBackgroundColor: Color;
  private xLabel: string;
  private yLabel: string;
  private xLabelStyle: string;
  private yLabelStyle: string;
  private xAxisOptions: any;
  private yAxisOptions: any;
  private xAxisColor: Color;
  private yAxisColor: Color;

  private buffer: SampleBuffer;
  private valueSample: DataSourceSample;

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
      // fontSize = (utils.parseIntChild(style, 'Fontsize') * (72 / 86)) + 'pt';
      fontSize = utils.parseIntChild(style, 'Fontsize');
      color = utils.parseColorChild(style, 'Color').toString();
      underline = utils.parseBooleanChild(style, 'IsUnderlined');
      italic = utils.parseBooleanChild(style, 'IsItalic');
      bold = utils.parseBooleanChild(style, 'IsBold');
    }
    let titleStyle = `font-family: ${fontFamily}; color: ${color}; font-size: ${fontSize}px`;
    titleStyle += (italic ? ';font-style: italic' : ';font-style: normal');
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
      axisLabelFormatter: (d: Date, gran: any) => this.formatHour(d),
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
    this.xLabelStyle = 'font-family: sans-serif; font-weight: normal; font-size: 12px';
    this.xAxisColor = Color.BLACK;
    if (utils.hasChild(defaultDomainAxis, 'AxisColor')) {
      this.xAxisColor = utils.parseColorChild(defaultDomainAxis, 'AxisColor');
      this.xAxisOptions['axisLineColor'] = this.xAxisColor.toString();
      this.xLabelStyle += `;color: ${this.xAxisColor}`;
    }

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
    this.yLabelStyle = 'font-family: sans-serif; font-weight: normal; font-size: 12px';
    this.yAxisColor = Color.BLACK;
    if (utils.hasChild(defaultRangeAxis, 'AxisColor')) {
      this.yAxisColor = utils.parseColorChild(defaultRangeAxis, 'AxisColor');
      this.yAxisOptions['axisLineColor'] = this.yAxisColor.toString();
      this.yLabelStyle += `;color: ${this.yAxisColor}`;
    }

    const expirationPeriod = utils.parseIntChild(this.node, 'ExpirationPeriod');
    if (expirationPeriod) {
      this.buffer = new ExpirationBuffer(expirationPeriod);
    } else {
      const expirationSamples = utils.parseIntChild(this.node, 'ExpirationSamples');
      this.buffer = new CircularBuffer(expirationSamples);
    }

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
    container.style.setProperty('position', 'absolute');
    container.style.setProperty('left', `${this.x}px`);
    container.style.setProperty('top', `${this.y}px`);
    container.style.setProperty('line-height', 'normal');

    // Second wrapper because Dygraphs will modify its style attributes
    const graphWrapper = document.createElement('div');
    graphWrapper.style.setProperty('background-color', this.graphBackgroundColor.toString());
    graphWrapper.style.setProperty('box-sizing', 'border-box');
    graphWrapper.style.setProperty('border', '1px solid #a6a6a6');
    container.appendChild(graphWrapper);

    // Some Dygraphs do not have a programmatic option. Use CSS instead
    const styleEl = document.createElement('style');
    styleEl.innerHTML = `
      #${containerId} .dygraph-xlabel { ${this.xLabelStyle} }
      #${containerId} .dygraph-ylabel { ${this.yLabelStyle} }
      #${containerId} .dygraph-axis-label-x {
        color: ${this.xAxisColor};
        font-family: sans-serif;
        font-weight: 100;
      }
      #${containerId} .dygraph-axis-label-y {
        color: ${this.yAxisColor};
        font-family: sans-serif;
        font-weight: 100;
      }
      #${containerId} .dygraph-legend {
        background-color: #eee;
        font-family: sans-serif;
        font-weight: 100;
        text-align: center;
        font-size: 12px;
      }
    `;
    container.appendChild(styleEl);

    this.display.container.appendChild(container);

    const series: {[key: string]: any} = {};
    series[this.yLabel] = {
      color: 'black',
      drawPoints: false,
      strokeWidth: 1,
      pointSize: 3,
    };

    this.graph = new Dygraph(graphWrapper, 'X\n', {
      title: this.title,
      titleHeight: this.titleHeight,
      fillGraph: false,
      interactionModel: {},
      width: this.width,
      height: this.height,
      xlabel: this.xLabel,
      ylabel: this.yLabel,
      labels: [this.xLabel, this.yLabel],
      series,
      labelsUTC: this.utc,
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

        // Add guidelines
        if (this.valueSample) {
          for (const range of this.valueSample.alarmRanges) {
            switch (range.level) {
              case 'WATCH':
              case 'WARNING':
                ctx.strokeStyle = Color.forName('yellow').toString();
                if (range.minInclusive !== undefined) {
                  this.drawGuideline(ctx, area, g, range.minInclusive);
                }
                if (range.maxInclusive !== undefined) {
                  this.drawGuideline(ctx, area, g, range.maxInclusive);
                }
                if (range.minExclusive !== undefined) {
                  this.drawGuideline(ctx, area, g, range.minExclusive);
                }
                if (range.maxExclusive !== undefined) {
                  this.drawGuideline(ctx, area, g, range.maxExclusive);
                }
                break;
              case 'DISTRESS':
              case 'CRITICAL':
              case 'SEVERE':
                ctx.strokeStyle = Color.forName('red').toString();
                if (range.minInclusive !== undefined) {
                  this.drawGuideline(ctx, area, g, range.minInclusive);
                }
                if (range.maxInclusive !== undefined) {
                  this.drawGuideline(ctx, area, g, range.maxInclusive);
                }
                if (range.minExclusive !== undefined) {
                  this.drawGuideline(ctx, area, g, range.minExclusive);
                }
                if (range.maxExclusive !== undefined) {
                  this.drawGuideline(ctx, area, g, range.maxExclusive);
                }
                break;
            }
          }
        }
      },
      legendFormatter: (data: any) => {
        let legend = data.xHTML + '<br>';
        for (const trace of data.series) {
          legend += `${trace.dashHTML} ${trace.yHTML}`;
        }
        return legend;
      }
    });
  }

  drawGuideline(ctx: CanvasRenderingContext2D, area: any, g: any, value: number) {
    ctx.beginPath();
    const y = g.toDomCoords(0, value)[1];
    ctx.moveTo(area.x, y);
    ctx.lineTo(area.x + area.w, y);
    ctx.stroke();
  }

  registerBinding(binding: DataSourceBinding) {
    switch (binding.dynamicProperty) {
      case 'VALUE':
        break;
      default:
        console.warn('Unsupported dynamic property: ' + binding.dynamicProperty);
    }
  }

  updateBinding(binding: DataSourceBinding, sample: DataSourceSample) {
    const value = binding.usingRaw ? sample.rawValue : sample.engValue;
    switch (binding.dynamicProperty) {
      case 'VALUE':
        this.valueSample = sample;
        if (sample.acquisitionStatus === 'EXPIRED') {
          this.buffer.push([sample.generationTime, null]);
        } else {
          this.buffer.push([sample.generationTime, value]);
        }
        break;
    }
  }

  digest() {
    const snapshot = this.buffer.snapshot();
    this.updateGraph(snapshot);
  }

  /**
   * The X label shows the day of the year for the visible period.
   */
  private updateGraph(samples: Sample[]) {
    let xlabel = this.xLabel;
    if (samples.length) {
      const first = this.formatDate(samples[0][0]);
      const last = this.formatDate(samples[samples.length - 1][0]);
      if (first === last) {
        xlabel = `${this.xLabel} [${first}]`;
      } else {
        xlabel = `${this.xLabel} [${first} - ${last}]`;
      }
    }

    this.graph.updateOptions({
      xlabel,
      file: samples.length ? samples : 'X\n',
      drawPoints: samples.length < 50,
    });
  }

  // Example: 01:02:03
  private formatHour(d: Date) {
    if (this.utc) {
      const hh = d.getUTCHours() < 10 ? '0' + d.getUTCHours() : d.getUTCHours();
      const mm = d.getUTCMinutes() < 10 ? '0' + d.getUTCMinutes() : d.getUTCMinutes();
      const ss = d.getUTCSeconds() < 10 ? '0' + d.getUTCSeconds() : d.getUTCSeconds();
      return `${hh}:${mm}:${ss}`;
    } else {
      const hh = d.getHours() < 10 ? '0' + d.getHours() : d.getHours();
      const mm = d.getMinutes() < 10 ? '0' + d.getMinutes() : d.getMinutes();
      const ss = d.getSeconds() < 10 ? '0' + d.getSeconds() : d.getSeconds();
      return `${hh}:${mm}:${ss}`;
    }
  }

  // Example: 27Jan18
  private formatDate(d: Date) {
    if (this.utc) {
      const dd = d.getUTCDate() < 10 ? '0' + d.getUTCDate() : d.getUTCDate();
      const mmm = MONTH_NAMES[d.getUTCMonth()];
      const yy = d.getUTCFullYear() % 100;
      return `${dd}${mmm}${yy}`;
    } else {
      const dd = d.getDate() < 10 ? '0' + d.getDate() : d.getDate();
      const mmm = MONTH_NAMES[d.getMonth()];
      const yy = d.getFullYear() % 100;
      return `${dd}${mmm}${yy}`;
    }
  }
}
