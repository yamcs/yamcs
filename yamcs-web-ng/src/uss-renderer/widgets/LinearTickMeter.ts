import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Parameter } from '../Parameter';

export class LinearTickMeter extends AbstractWidget {

  meterMin: number;
  meterMax: number;
  meterRange: number;
  meterHeight: number;

  labelStyle: string;
  drawLabels: boolean;
  tickMajorFreq: number;
  tickColor: string;

  parseAndDraw(svg: any, parent: any, e: Node) {
    const cx = this.width / 2;
    const cy = this.height / 2;

    this.meterMin = utils.parseFloatChild(e, 'Minimum');
    this.meterMax = utils.parseFloatChild(e, 'Maximum');
    this.meterRange = this.meterMax - this.meterMin;

    const orientation = utils.parseStringChild(e, 'Orientation').toLowerCase();

    let meterHeight;
    let transform;
    if (orientation === 'vertical') {
      transform = { transform: `translate(${this.x + 15},${this.y + 10})` };
      meterHeight = this.height - 20;
    } else {
      transform = {transform: `translate(${this.width - 10} ${cy}) rotate(90)`};
      meterHeight = this.width - 20;
    }
    this.meterHeight = meterHeight;


    this.labelStyle = utils.parseStringChild(e, 'LabelStyle').toLowerCase();
    this.drawLabels = this.labelStyle !== 'no_labels';
    const tickBase = utils.parseFloatChild(e, 'TickBase', 0);
    const tickUnit = utils.parseFloatChild(e, 'TickUnit');
    this.tickMajorFreq = utils.parseFloatChild(e, 'TickMajorFrequency');
    this.tickColor = utils.parseColorChild(e, 'Color', 'black');

    // svg.rect(parent, 0, 0, width, height, {fill: 'none', strokeWidth: '1px'});

    const g = svg.group(parent, transform);

    svg.rect(g, -3, 0, 6, meterHeight, { fill: 'white', stroke: 'none' });

    let idx = 0;
    let tickStart = tickBase;
    if (tickStart > this.meterMax) {
      tickStart = this.meterMax;
    }
    for (let tick = tickStart; tick >= this.meterMin; tick -= tickUnit, idx++) {
      this.paintTick(tick, idx);
    }

    if (idx > 0) {
      idx = 1;
    }
    tickStart = tickBase + tickUnit;
    if (tickStart < this.meterMin) {
      tickStart = this.meterMin;
    }
    for (let tick = tickStart; tick <= this.meterMax; tick += tickUnit, idx++) {
      this.paintTick(tick, idx);
    }

    svg.rect(g, -2.5, meterHeight, 6, 0, {
      id: this.id + '-indicator',
      fill: '#00FF00',
      stroke: 'black',
      strokeWidth: '1px',
    });
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
    indicator.setAttribute('y', this.meterHeight - pos);
    indicator.setAttribute('height', pos);
  }

  private paintTick(tick: number, idx: number) {
    const settings: { [key: string]: string } = {
      fill: 'none',
      stroke: this.tickColor,
      strokeWidth: '1px',
    };
    const pos = this.meterHeight - this.getIndicatorHeight(tick);
    // whether to do a major or minor tick
    if (idx % this.tickMajorFreq === 0) {
      settings.strokeWidth = '2px';
      svg.line(g, -8.5, pos, 8.5, pos, settings);
      if (this.drawLabels) {
        let posX = -14;
        const textSettings: {[key: string]: string} = { fontSize: '10' };

        switch (this.labelStyle) {
        case 'left_or_top':
          posX = -14;
          textSettings.textAnchor = 'end';
          break;

        case 'right_or_bottom':
          posX = 14;
          textSettings.textAnchor = 'start';
          break;

        case 'alternate_start_left_or_top':
          posX = (idx % 4 === 0) ? -14 : 14;
          textSettings.textAnchor = (idx % 4 === 0) ? 'end' : 'start';
          break;

        case 'alternate_start_right_or_bottom':
          posX = (idx % 4 === 0) ? 14 : -14;
          textSettings.textAnchor = (idx % 4 === 0) ? 'start' : 'end';
          break;
        }

        let posY;
        if (orientation === 'horizontal') {
          posX = -posX;
          if (posX === 14) {
            posX = posX + 5;
          }
          posY = pos;
          textSettings.textAnchor = 'middle';
          textSettings.transform = `rotate(-90 ${posX} ${posY})`;
        } else {
          posY = pos + 3;
        }

        svg.text(g, posX, posY, '' + tick, textSettings);
      }
    } else {
      svg.line(g, -6, pos, 6, pos, settings);
    }
  }
}
