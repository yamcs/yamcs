import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Parameter } from '../Parameter';
import { G, Rect, Line, Text } from '../tags';
import { Color } from '../Color';

export class LinearTickMeter extends AbstractWidget {

  meterMin: number;
  meterMax: number;
  meterRange: number;
  meterHeight: number;

  labelStyle: string;
  drawLabels: boolean;
  tickMajorFreq: number;
  tickColor: Color;

  parseAndDraw() {
    // const cx = this.width / 2;
    const cy = this.height / 2;

    this.meterMin = utils.parseFloatChild(this.node, 'Minimum');
    this.meterMax = utils.parseFloatChild(this.node, 'Maximum');
    this.meterRange = this.meterMax - this.meterMin;

    const orientation = utils.parseStringChild(this.node, 'Orientation').toLowerCase();

    let meterHeight;
    let transform;
    if (orientation === 'vertical') {
      transform = `translate(${this.x + 15},${this.y + 10})`;
      meterHeight = this.height - 20;
    } else {
      transform = `translate(${this.width - 10} ${cy}) rotate(90)`;
      meterHeight = this.width - 20;
    }
    this.meterHeight = meterHeight;

    this.labelStyle = utils.parseStringChild(this.node, 'LabelStyle').toLowerCase();
    this.drawLabels = this.labelStyle !== 'no_labels';
    const tickBase = utils.parseFloatChild(this.node, 'TickBase', 0);
    const tickUnit = utils.parseFloatChild(this.node, 'TickUnit');
    this.tickMajorFreq = utils.parseFloatChild(this.node, 'TickMajorFrequency');
    this.tickColor = utils.parseColorChild(this.node, 'Color', new Color(0, 0, 0, 0));

    // svg.rect(parent, 0, 0, width, height, {fill: 'none', strokeWidth: '1px'});

    const g = new G({
      transform
    }).addChild(new Rect({
      class: 'linear-tick-meter',
      'data-name': this.name,
      x: -3,
      y: 0,
      width: 6,
      height: meterHeight,
      fill: 'white',
      stroke: 'none',
    }));

    let idx = 0;
    let tickStart = tickBase;
    if (tickStart > this.meterMax) {
      tickStart = this.meterMax;
    }
    for (let tick = tickStart; tick >= this.meterMin; tick -= tickUnit, idx++) {
      this.paintTick(g, tick, idx);
    }

    if (idx > 0) {
      idx = 1;
    }
    tickStart = tickBase + tickUnit;
    if (tickStart < this.meterMin) {
      tickStart = this.meterMin;
    }
    for (let tick = tickStart; tick <= this.meterMax; tick += tickUnit, idx++) {
      this.paintTick(g, tick, idx);
    }

    g.addChild(new Rect({
      id: this.id + '-indicator',
      x: -2.5,
      y: meterHeight,
      width: 6,
      height: 0,
      fill: '#00FF00',
      stroke: 'black',
      'stroke-width': '1px',
    }));

    return g;
  }

  getIndicatorHeight(val: any) {
    return (val + Math.abs(this.meterMin)) / this.meterRange * this.meterHeight;
  }

  updateValue(para: Parameter, usingRaw: boolean) {
    const value = this.getParameterValue(para, usingRaw);
    let pos = this.getIndicatorHeight(value);
    if (pos > this.meterHeight) {
      pos = this.meterHeight;
    }
    const indicator = this.svg.getElementById(this.id + '-indicator');
    indicator.setAttribute('y', String(this.meterHeight - pos));
    indicator.setAttribute('height', String(pos));
  }

  private paintTick(g: G, tick: number, idx: number) {
    const styleAttributes: { [key: string]: any } = {
      fill: 'none',
      stroke: this.tickColor,
      strokeWidth: '1px',
    };
    const pos = this.meterHeight - this.getIndicatorHeight(tick);
    // whether to do a major or minor tick
    if (idx % this.tickMajorFreq === 0) {
      g.addChild(new Line({
        x1: -8.5,
        y1: pos,
        x2: 8.5,
        y2: pos,
        ...styleAttributes,
        'stroke-width': '2px',
      }));
      if (this.drawLabels) {
        let posX = -14;
        const textAttributes: { [key: string]: string } = { fontSize: '10' };

        switch (this.labelStyle) {
          case 'left_or_top':
            posX = -14;
            textAttributes['text-anchor'] = 'end';
            break;
          case 'right_or_bottom':
            posX = 14;
            textAttributes['text-anchor'] = 'start';
            break;
          case 'alternate_start_left_or_top':
            posX = (idx % 4 === 0) ? -14 : 14;
            textAttributes['text-anchor'] = (idx % 4 === 0) ? 'end' : 'start';
            break;
          case 'alternate_start_right_or_bottom':
            posX = (idx % 4 === 0) ? 14 : -14;
            textAttributes['text-anchor'] = (idx % 4 === 0) ? 'start' : 'end';
            break;
        }

        let posY;
        if (orientation === 'horizontal') {
          posX = -posX;
          if (posX === 14) {
            posX = posX + 5;
          }
          posY = pos;
          textAttributes['text-anchor'] = 'middle';
          textAttributes['transform'] = `rotate(-90 ${posX} ${posY})`;
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
        x1: -6,
        y1: pos,
        x2: 6,
        y2: pos,
        ...styleAttributes,
      }));
    }
  }
}
