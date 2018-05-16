import { ParameterValue } from '@yamcs/client';
import Dygraph from 'dygraphs';
import { Circle, G, Rect, Text } from '../../tags';
import { CircularBuffer } from '../CircularBuffer';
import { Color } from '../Color';
import { DataSourceBinding } from '../DataSourceBinding';
import { ExpirationBuffer } from '../ExpirationBuffer';
import { ParameterBinding } from '../ParameterBinding';
import { ParameterSample } from '../ParameterSample';
import { Sample, SampleBuffer } from '../SampleBuffer';
import { DEFAULT_STYLE } from '../StyleSet';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';
import { convertMonitoringResult } from './Field';

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const PLOT_COLORS = [
  Color.BLACK,
  new Color(0, 0, 255, 255),
  // FIXME Below colors are made up and not same as uss
  new Color(0, 210, 213, 255),
  new Color(231, 41, 138, 255),
  new Color(65, 171, 93, 255),
  new Color(102, 194, 165, 255),
];

const indicatorChars = 2;

interface LegendData {
  valueBinding: ParameterBinding;

  elId: string;
  el?: Element;

  backgroundElId: string;
  backgroundEl?: Element;
}

/**
 * TODO
 * - interaction model for y or x scroll
 * - interaction model for ctrl y or x zoom
 * - dynamically update xlabelwidth based on tick metrics
 */
export class LineGraph extends AbstractWidget {

  private graph: any;

  private utc = true;

  private title: string;
  private titleHeight: number;
  private legendHeight: number;
  private legendDecimals: number;
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

  private valueBindings: ParameterBinding[];
  private buffer: SampleBuffer;

  private legendDataSet: LegendData[] = [];

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

    const legendG = this.parseAndDrawLegend();
    return new G({
      id: this.id,
      class: 'line-graph',
      translate: `translate(${this.x},${this.y})`,
      'data-name': this.name,
    }).addChild(legendG);
  }

  parseAndDrawLegend() {
    const g = new G();
    this.legendHeight = 0;
    if (utils.parseBooleanChild(this.node, 'LegendEnabled')) {
      this.legendDecimals = utils.parseIntChild(this.node, 'LegendFieldDecimals');
      const columns = utils.parseIntChild(this.node, 'LegendFieldColumns');
      const effectiveColumns = columns + indicatorChars;

      const fontFamily = 'Lucida Sans Typewriter';
      const fontSize = '10px';
      const fm = this.getFontMetrics('i', fontFamily, 'normal', 'normal', fontSize);
      const colSize = Math.floor(fm.width);
      const boxWidth = colSize * effectiveColumns;

      for (let i = 0; i < this.valueBindings.length; i++) {
        const valueBinding = this.valueBindings[i];
        this.legendHeight += 10;
        const boxHeight = 10;

        const backgroundElId = this.generateChildId();
        g.addChild(new Rect({
          id: backgroundElId,
          x: this.x + this.width - boxWidth,
          y: this.y + (i * boxHeight),
          width: boxWidth,
          height: boxHeight,
          fill: this.styleSet.getStyle('NOT_RECEIVED').bg.toString(),
          'shape-rendering': 'crispEdges',
        }));

        g.addChild(new Circle({
          cx: this.x + this.width - boxWidth - 10,
          cy: this.y + (i * boxHeight) + Math.ceil(boxHeight / 2),
          r: 4,
          fill: PLOT_COLORS[i],
        }));

        g.addChild(new Text({
          x: this.x + this.width - boxWidth - 20,
          y: this.y + (i * boxHeight) + Math.ceil(boxHeight / 2),
          'dominant-baseline': 'middle',
          'font-family': fontFamily,
          'font-size': fontSize,
          'text-anchor': 'end',
          fill: Color.BLACK.toString(),
        }, valueBinding.opsName));

        const elId = this.generateChildId();
        g.addChild(new Text({
          id: elId,
          x: this.x + this.width - (colSize * indicatorChars),
          y: this.y + (i * boxHeight) + Math.ceil(boxHeight / 2),
          'dominant-baseline': 'middle',
          'font-family': fontFamily,
          'font-size': fontSize,
          'text-anchor': 'end',
          fill: this.styleSet.getStyle('NOT_RECEIVED').fg.toString(),
        }));

        this.legendDataSet.push({ valueBinding, elId, backgroundElId });
      }
    }
    return g;
  }

  afterDomAttachment() {
    for (const legendData of this.legendDataSet) {
      legendData.el = this.svg.getElementById(legendData.elId);
      legendData.backgroundEl = this.svg.getElementById(legendData.backgroundElId);
    }

    // First wrapper positions within the display
    // (LineGraphs are rendered outside of SVG)
    const container = document.createElement('div');
    const containerId = this.generateChildId();
    container.setAttribute('id', containerId);
    container.style.setProperty('position', 'absolute');
    container.style.setProperty('left', `${this.x}px`);
    container.style.setProperty('top', `${this.y + this.legendHeight}px`);
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
      color: PLOT_COLORS[0],
      drawPoints: false,
      strokeWidth: 1,
      pointSize: 3,
    };

    const extraLabels: string[] = [];
    for (let i = 1; i < this.valueBindings.length; i++) {
      // Exact name doesn't matter, as long as it's unique
      const label = this.valueBindings[i].opsName!;
      extraLabels.push(label);
      series[label] = {
        color: PLOT_COLORS[i],
        drawPoints: false,
        strokeWidth: 1,
        pointSize: 3,
      };
    }

    this.graph = new Dygraph(graphWrapper, 'X\n', {
      title: this.title,
      titleHeight: this.titleHeight,
      fillGraph: false,
      interactionModel: {},
      width: this.width,
      height: this.height - this.legendHeight,
      xlabel: this.xLabel,
      ylabel: this.yLabel,
      labels: [this.xLabel, this.yLabel, ...extraLabels],
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
        for (const valueBinding of this.valueBindings) {
          if (valueBinding.sample) {
            for (const range of valueBinding.sample.alarmRanges) {
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
        this.valueBindings = this.valueBindings || [];
        this.valueBindings.push(binding);
        break;
      default:
        console.warn('Unsupported dynamic property: ' + binding.dynamicProperty);
    }
  }

  // Don't use onBindingUpdate because that gets triggered for every parameter
  // separately whereas our plot buffer needs combined data.
  onDelivery(pvals: ParameterValue[]) {
    let generationTime;
    const values: Array<number | null> = [];
    for (const binding of this.valueBindings) {
      let inDelivery = false;
      for (const pval of pvals) {
        if (binding.opsName === pval.id.name) {
          const sample = new ParameterSample(pval);
          generationTime = sample.generationTime;
          if (sample.acquisitionStatus === 'EXPIRED') {
            values.push(null);
          } else {
            values.push(binding.usingRaw ? sample.rawValue : sample.engValue);
          }
          inDelivery = true;
          break;
        }
      }

      if (!inDelivery) {
        values.push(binding.value);
      }
    }

    if (generationTime) {
      this.buffer.push([generationTime, ...values] as Sample);
      // console.log('values', values);
    }
  }

  digest() {
    if (this.legendHeight) {
      for (const valueBinding of this.valueBindings) {
        if (valueBinding.sample) {
          let legendEl: Element;
          let legendBackgroundEl: Element;
          for (const legendData of this.legendDataSet) {
            if (legendData.valueBinding === valueBinding) {
              legendEl = legendData.el!;
              legendBackgroundEl = legendData.backgroundEl!;
            }
          }
          const sample = valueBinding.sample;
          const cdmcsMonitoringResult = convertMonitoringResult(sample);
          let v = valueBinding.value;
          v = v.toFixed(this.legendDecimals);
          legendEl!.textContent = v;
          let style = DEFAULT_STYLE;
          switch (sample.acquisitionStatus) {
            case 'ACQUIRED':
              style = this.styleSet.getStyle('ACQUIRED', cdmcsMonitoringResult);
              break;
            case 'NOT_RECEIVED':
              style = this.styleSet.getStyle('NOT_RECEIVED');
              break;
            case 'INVALID':
              style = this.styleSet.getStyle('INVALID');
              break;
            case 'EXPIRED':
              style = this.styleSet.getStyle('STATIC', cdmcsMonitoringResult);
              break;
          }
          legendBackgroundEl!.setAttribute('fill', style.bg.toString());
          legendEl!.setAttribute('fill', style.fg.toString());
        }
      }
    }

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
