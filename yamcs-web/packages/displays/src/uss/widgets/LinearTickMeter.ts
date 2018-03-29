import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { G, Rect, Line, Text } from '../../tags';
import { Color } from '../Color';
import { DataSourceBinding } from '../DataSourceBinding';

export class LinearTickMeter extends AbstractWidget {

  meterMin: number;
  meterMax: number;
  meterRange: number;
  meterHeight: number;

  padding: number;
  labelStyle: string;
  drawLabels: boolean;
  tickMajorFreq: number;
  tickColor: Color;

  orientation: string;

  private valueBinding: DataSourceBinding;

  parseAndDraw() {
    this.padding = 10;
    this.meterMin = utils.parseFloatChild(this.node, 'Minimum');
    this.meterMax = utils.parseFloatChild(this.node, 'Maximum');
    this.meterRange = this.meterMax - this.meterMin;

    this.labelStyle = utils.parseStringChild(this.node, 'LabelStyle');
    this.drawLabels = this.labelStyle !== 'NO_LABELS';
    const tickBase = utils.parseFloatChild(this.node, 'TickBase', 0);
    const tickUnit = utils.parseFloatChild(this.node, 'TickUnit');
    this.tickMajorFreq = utils.parseFloatChild(this.node, 'TickMajorFrequency');
    this.tickColor = utils.parseColorChild(this.node, 'Color', Color.BLACK);

    /*
     * BACKGROUND
     */
    const g = new G({
      class: 'linear-tick-meter',
      transform: `translate(${this.x}, ${this.y})`,
      'data-name': this.name,
    }).addChild(new Rect({
      x: 0,
      y: 0,
      width: this.width,
      height: this.height,
      ...utils.parseFillStyle(this.node),
    }));

    if (utils.hasChild(this.node, 'BorderColor')) {
      g.addChild(new Rect({
        fill: 'none',
        stroke: utils.parseColorChild(this.node, 'BorderColor'),
        'stroke-width': 1,
        'shape-rendering': 'crispEdges',
      }).withBorderBox(0, 0, this.width, this.height));
    }

    if (utils.hasChild(this.node, 'Label')) {
      const label = utils.findChild(this.node, 'Label').textContent || '';
      const textStyle = utils.findChild(this.node, 'LabelTextStyle');
      g.addChild(new Text({
        x: this.width / 2,
        y: this.height - this.padding,
        'dominant-baseline': 'middle',
        'text-anchor': 'middle',
        ...utils.parseTextStyle(textStyle),
      }, label));
    }

    /*
     * INDICATOR CONTAINER
     * (possibly rotated)
     */
    const indicatorG = new G();
    // Work with origin in the center of the meter
    let transform = `translate(${this.width / 2}, ${this.height / 2})`;
    this.orientation = utils.parseStringChild(this.node, 'Orientation');
    if (this.orientation === 'VERTICAL') {
      this.meterHeight = this.height - (2 * this.padding);
      if (utils.hasChild(this.node, 'Label')) {
        // Pretty random padding, should maybe derive from label fontSize
        const extraBottomPadding = 2 * this.padding;
        this.meterHeight -= extraBottomPadding;
        transform += ` translate(0, ${- extraBottomPadding / 2})`;
      }
    } else {
      transform += ' rotate(90)';
      this.meterHeight = this.width - (2 * this.padding);
    }
    indicatorG.setAttribute('transform', transform);
    g.addChild(indicatorG);

    const tickIndicator = utils.findChild(this.node, 'TickIndicator');
    const meterWidth = utils.parseFloatChild(tickIndicator, 'Thickness');
    indicatorG.addChild(new Rect({
      x: - (meterWidth / 2),
      y: - (this.meterHeight / 2),
      width: meterWidth,
      height: this.meterHeight,
      fill: utils.parseColorChild(tickIndicator, 'BackgroundColor'),
      stroke: 'none',
      'shape-rendering': 'crispEdges',
    }));

    /*
     * TICKS
     */
    const indicatorTextStyle = utils.findChild(this.node, 'IndicatorTextStyle');
    const indicatorProps = utils.parseTextStyle(indicatorTextStyle);

    let idx = 0;
    let tickStart = tickBase;
    if (tickStart > this.meterMax) {
      tickStart = this.meterMax;
    }
    for (let tick = tickStart; tick >= this.meterMin; tick -= tickUnit, idx++) {
      this.paintTick(indicatorG, tick, idx, indicatorProps);
    }

    if (idx > 0) {
      idx = 1;
    }
    tickStart = tickBase + tickUnit;
    if (tickStart < this.meterMin) {
      tickStart = this.meterMin;
    }
    for (let tick = tickStart; tick <= this.meterMax; tick += tickUnit, idx++) {
      this.paintTick(indicatorG, tick, idx, indicatorProps);
    }

    /*
     * INDICATOR BAR
     */
    const indicatorType = utils.parseStringAttribute(tickIndicator, 'class');
    switch (indicatorType) {
      case 'TickIndicatorBar':
        const x = - (meterWidth / 2);
        indicatorG.addChild(new Rect({
          id: this.id + '-indicator',
          fill: '#00FF00',
          stroke: 'black',
          'stroke-width': 1,
          'shape-rendering': 'crispEdges',
        }).withBorderBox(x, - (this.meterHeight / 2) + this.meterHeight - 30, meterWidth, 30));
        break;

      default:
        console.warn(`Unsupported tick indicator ${indicatorType}`);
    }

    return g;
  }

  getIndicatorHeight(val: any) {
    return (val + Math.abs(this.meterMin)) / this.meterRange * this.meterHeight;
  }

  private paintTick(g: G, tick: number, idx: number, indicatorProps: {}) {
    const styleAttributes: { [key: string]: any } = {
      fill: 'none',
      stroke: this.tickColor,
      strokeWidth: 1,
    };
    const pos = - (this.meterHeight / 2) + this.meterHeight - this.getIndicatorHeight(tick);
    // whether to do a major or minor tick
    if (idx % this.tickMajorFreq === 0) {
      g.addChild(new Line({
        x1: -10,
        y1: pos,
        x2: 10,
        y2: pos,
        ...styleAttributes,
        'stroke-width': 2,
        'shape-rendering': 'crispEdges',
      }));
      if (this.drawLabels) {
        let posX = -14;
        const textAttributes: { [key: string]: any } = {
          ...indicatorProps,
        };

        switch (this.labelStyle) {
          case 'LEFT_OR_TOP':
            posX = -14;
            textAttributes['text-anchor'] = 'end';
            break;
          case 'RIGHT_OR_BOTTOM':
            posX = 14;
            textAttributes['text-anchor'] = 'start';
            break;
          case 'ALTERNATE_START_LEFT_OR_TOP':
            posX = (idx % 4 === 0) ? -14 : 14;
            textAttributes['text-anchor'] = (idx % 4 === 0) ? 'end' : 'start';
            break;
          case 'ALTERNATE_START_RIGHT_OR_BOTTOM':
            posX = (idx % 4 === 0) ? 14 : -14;
            textAttributes['text-anchor'] = (idx % 4 === 0) ? 'start' : 'end';
            break;
        }

        let posY;
        if (this.orientation === 'HORIZONTAL') {
          if (this.labelStyle === 'ALTERNATE_START_LEFT_OR_TOP' || this.labelStyle === 'ALTERNATE_START_RIGHT_OR_BOTTOM') {
            posX = -posX;
          }
          if (posX === 14) {
            posX = posX + 5;
          }
          posY = pos;
          textAttributes['text-anchor'] = 'middle';
          textAttributes['transform'] = `translate(0, 0) rotate(-90 ${posX} ${posY})`;
        } else {
          posY = pos + 3;
        }

        g.addChild(new Text({
          x: posX,
          y: posY,
          ...textAttributes,
        }, '' + tick));
      }
    } else {
      g.addChild(new Line({
        x1: - 5,
        y1: pos,
        x2: 5,
        y2: pos,
        ...styleAttributes,
        'shape-rendering': 'crispEdges',
      }));
    }
  }

  registerBinding(binding: DataSourceBinding) {
    switch (binding.dynamicProperty) {
      case 'VALUE':
        this.valueBinding = binding;
        break;
      default:
        console.warn('Unsupported binding to property: ' + binding.dynamicProperty);
    }
  }

  digest() {
    if (this.valueBinding && this.valueBinding.sample) {
      const value = this.valueBinding.value;
      this.updateValue(value);
    }
  }

  private updateValue(value: any) {
    let pos = this.getIndicatorHeight(value);
    if (pos > this.meterHeight) {
      pos = this.meterHeight;
    }
    const indicator = this.svg.getElementById(this.id + '-indicator');
    indicator.setAttribute('y', String(this.meterHeight - pos));
    indicator.setAttribute('height', String(pos));
  }
}
