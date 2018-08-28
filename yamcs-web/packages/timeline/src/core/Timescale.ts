import { Action } from '../Action';
import RenderContext from '../RenderContext';
import { Defs, G, Line, Pattern, Rect, Svg, Text } from '../tags';
import Timeline from '../Timeline';
import * as utils from '../utils';
import { generateId } from '../utils';
import Band, { BandOptions } from './Band';

const SCALE_1H = 1;
const SCALE_QD = 2;
const SCALE_1D = 3;
const SCALE_5D = 4;
const SCALE_1M = 5;

export interface TimescaleOptions extends BandOptions {
  tz: string;
  resolution?: 'auto';
  dayFormat?: string;

  /**
   * Which action to perform when the user performs a grab operation anywhere on the band.
   * Default: pan
   */
  grabAction?: 'pan' | 'select';
}

export interface TimescaleStyle {
  divisionWidth: number;
  lineHeight: number;
  textColor: string;
  textSize: number;
  majorTickColor: string;
  majorTickWidth: number;
  midTickColor: string;
  midTickWidth: number;
  minorTickColor: string;
  minorTickWidth: number;
  bandBackgroundColor: string;
  horizontalTickLineColor: string;
  horizontalTickLineWidth: number;
}

/**
 * TIMESCALE
 *
 * For deeper zooms we should draw inspiration from video editing software
 * where they just show sth like HH:mm:ss at rounded locations, and then
 * with vertical dividers to make it more explicit
 */
export default class Timescale extends Band {

  static get type(): string {
    return 'Timescale';
  }

  static get rules() {
    return {
      textColor: 'grey',
      majorTickColor: '#aaa',
      majorTickWidth: 1,
      minorTickColor: '#aaa',
      minorTickWidth: 1,
      midTickColor: '#aaa',
      midTickWidth: 1,
      adjust: true,
      dark: {
        horizontalTickLineColor: '#333',
        horizontalTickLineWidth: 2,
      },
    };
  }

  scale: number;
  dayFormat: any;
  contributionId: any;

  scaleBgId: string;

  grabX1?: Date;

  constructor(timeline: Timeline, protected opts: TimescaleOptions, protected style: TimescaleStyle) {
    super(timeline, opts, style);

    if (!opts.resolution || opts.resolution === 'auto') {
      if (this._testSpread(60 * 60 * 1000)) {
        this.scale = SCALE_1H;
      } else if (this._testSpread(6 * 60 * 60 * 1000)) {
        this.scale = SCALE_QD;
      } else if (this._testSpread(24 * 60 * 60 * 1000)) {
        this.scale = SCALE_1D;
      } else if (this._testSpread(5 * 24 * 60 * 60 * 1000)) {
        this.scale = SCALE_5D;
      } else {
        this.scale = SCALE_1M;
      }
    } else {
      // TODO
    }

    this.dayFormat = this.opts.dayFormat || 'DDDD';
    this.contributionId = generateId();
  }

  /**
   * The important thing here is that ticks are drawn using SVG patterns. This makes
   * it lighter in terms of DOM Size. However, we need to do some tricks to correctly
   * align them with the rest of the scale.
   */
  renderViewport(ctx: RenderContext) {
    // visibleStart might not be exactly aligned on the hour, so offset our pattern
    // TODO i think we need to account for an offset between visibleStart and loadStart here
    // TODO because the rect (the hour) begins at loadStart. Not visibleStart.
    const startOfHour = utils.startOfHour(this.timeline.visibleStart);
    const offsetX = this.timeline.pointsBetween(this.timeline.visibleStart, startOfHour);

    const tickSvg = new Svg({
      x: ctx.x + offsetX,
      y: ctx.y,
      height: this.style.lineHeight,
      style: 'overflow: visible',
    }).addChild(new Defs().addChild(this._renderHourPattern()));

    this.scaleBgId = generateId();
    const scaleBg: any = {
      id: this.scaleBgId,
      x: this.timeline.positionDate(this.timeline.loadStart),
      y: 0,
      width: this.timeline.pointsBetween(this.timeline.loadStart, this.timeline.loadStop) - offsetX,
      height: this.style.lineHeight,
      fill: this.style.bandBackgroundColor,
    };

    if (this.opts.grabAction === 'select') {
      scaleBg['cursor'] = 'col-resize';
      this.timeline.registerActionTarget('click', this.scaleBgId);
      this.timeline.registerActionTarget('grabstart', this.scaleBgId);
      this.timeline.registerActionTarget('grabmove', this.scaleBgId);
      this.timeline.registerActionTarget('grabend', this.scaleBgId);
    } else {
      scaleBg['pointer-events'] = 'none';
    }

    if (this.style.horizontalTickLineColor) {
      scaleBg['stroke'] = this.style.horizontalTickLineColor;
    }
    if (this.style.horizontalTickLineWidth) {
      scaleBg['stroke-width'] = this.style.horizontalTickLineWidth;
    }

    tickSvg.addChild(new Rect(scaleBg));

    const labelSvg = new Svg({
      x: ctx.x,
      y: ctx.y,
      height: this.style.lineHeight,
      style: 'overflow: visible',
    });
    if (this.scale === SCALE_1H) {
      scaleBg['fill'] = `url(#${this.contributionId}_1h)`,
      labelSvg.addChild(...this._renderHoursScale(this.opts.tz));
    } else if (this.scale === SCALE_QD) {
      labelSvg.addChild(...this._renderQuarterDaysScale(this.opts.tz));
    } else if (this.scale === SCALE_1D) {
      labelSvg.addChild(...this._renderWeekDaysScale(this.opts.tz));
    } else if (this.scale === SCALE_5D) {
      labelSvg.addChild(...this._renderWeeksScale(this.opts.tz));
    } else {
      labelSvg.addChild(...this._renderMonthsScale(this.opts.tz));
    }

    return new G().addChild(tickSvg, labelSvg);
  }

  /**
   * Estimates if the specified step (in milliseconds) gives a
   * visually pleasing spread when rendering all steps
   */
  _testSpread(step: number) {
    const startX = this.timeline.positionDate(this.timeline.loadStart);
    const testDate = utils.addMillis(this.timeline.loadStart, step);
    const x = this.timeline.positionDate(testDate);
    const xDiff = x - startX;
    return (xDiff > this.timeline.style.divisionWidth * 2);
  }

  _renderHourPattern() {
    // Make the pattern the same size as one hour
    const width = this.style.divisionWidth * (60 * 60 / this.timeline.secondsPerDivision);
    const height = this.style.lineHeight;

    // it might be that we need to offset due to the visible start
    // position not aligning exactly on the hour. However, we do this
    // offsetting not here, but on the parent svg, because otherwise SVG
    // does some weird rounding shenanigans which makes our ticks disappear.
    return new Pattern({
      id: this.contributionId + '_1h',
      x: 0,
      y: 0,
      width,
      height,
      patternUnits: 'userSpaceOnUse',
    }).addChild(
      new Line({
        x1: 0, y1: 0,
        x2: 0, y2: height,
        stroke: this.style.majorTickColor,
        'stroke-width': this.style.majorTickWidth,
      }),
      new Line({
        x1: width * 0.25, y1: height * 0.8,
        x2: width * 0.25, y2: height,
        stroke: this.style.minorTickColor,
        'stroke-width': this.style.minorTickWidth,
      }),
      new Line({
        x1: width * 0.5, y1: height * 0.6,
        x2: width * 0.5, y2: height,
        stroke: this.style.midTickColor,
        'stroke-width': this.style.midTickWidth,
      }),
      new Line({
        x1: width * 0.75, y1: height * 0.8,
        x2: width * 0.75, y2: height,
        stroke: this.style.minorTickColor,
        'stroke-width': this.style.minorTickWidth,
      }),
    );
  }

  _renderHoursScale(tz: string) {
    const result = [];

    // Trunc it to hours before positioning
    const date = utils.startOfHour(this.timeline.loadStart);

    while (date.getTime() <= this.timeline.loadStop.getTime()) {
      const x = this.timeline.positionDate(date);
      const label = utils.formatDate(date, 'HH', tz);
      if (label === '00') {
        result.push(new Text({
          x: x + 2,
          y: this.style.lineHeight / 4,
          cursor: 'default',
          fill: this.style.textColor,
          'font-size': this.style.textSize,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'pointer-events': 'none'
        }, utils.formatDate(date, 'MMM', tz) + ' ' + utils.formatDate(date, 'DD', tz)));
        result.push(new Text({
          x: x + 2,
          y: this.style.lineHeight * 0.75,
          cursor: 'default',
          fill: this.style.textColor,
          'font-size': this.style.textSize,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'pointer-events': 'none'
        }, label));
      } else {
        result.push(new Text({
          x: x + 2,
          y: this.style.lineHeight / 2,
          cursor: 'default',
          fill: this.style.textColor,
          'font-size': this.style.textSize,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'pointer-events': 'none'
        }, label));
      }
      date.setTime(date.getTime() + (60 * 60 * 1000));
    }

    return result;
  }

  _renderQuarterDaysScale(tz: string) {
    const result = [];
    const date = utils.startOfDay(this.timeline.loadStart, tz);

    while (date.getTime() <= this.timeline.loadStop.getTime()) {
      const x = this.timeline.positionDate(date);
      const label = utils.formatDate(date, 'HH', tz);
      if (label === '00') {
        result.push(new Line({
          x1: x, y1: 0,
          x2: x, y2: this.style.lineHeight,
          stroke: this.style.majorTickColor,
          'stroke-width': this.style.majorTickWidth,
          'pointer-events': 'none'
        }));
        result.push(new Text({
          x: x + 2,
          y: this.style.lineHeight / 4,
          cursor: 'default',
          fill: this.style.textColor,
          'font-size': this.style.textSize,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'pointer-events': 'none'
        }, utils.formatDate(date, 'ddd', tz) + ' ' + utils.formatDate(date, 'DD', tz) + '/' + utils.formatDate(date, 'MM', tz)));
      } else {
        result.push(new Line({
          x1: x, y1: this.style.lineHeight / 2,
          x2: x, y2: this.style.lineHeight,
          stroke: this.style.majorTickColor,
          'stroke-width': this.style.majorTickWidth,
          'pointer-events': 'none'
        }));
      }

      if (tz === 'GMT' || tz === 'UTC') {
        // Force full duration of 6 hours
        date.setTime(date.getTime() + (6 * 60 * 60 * 1000));
      } else {
        // Add 'about' 6 hours, depending on DST transition (e.g. CET/CEST)
        // This to prevent labels changing to 01, 07, 13, ... instead of 00, 06, 12
        date.setHours(date.getHours() + 6);
      }

      const x2 = this.timeline.positionDate(date);
      result.push(new Text({
        x: ((x + x2) / 2) + 2,
        y: this.style.lineHeight * 0.75,
        cursor: 'default',
        fill: this.style.textColor,
        'font-size': this.style.textSize,
        'text-anchor': 'middle',
        'dominant-baseline': 'middle',
        'pointer-events': 'none'
      }, label));
    }

    return result;
  }

  _renderDaysScale(tz: string) {
    const result = [];

    // Trunc to a day start before positioning
    const date = utils.startOfDay(this.timeline.loadStart, tz);

    while (date.getTime() <= this.timeline.loadStop.getTime()) {
      const x = this.timeline.positionDate(date);
      result.push(new Line({
        x1: x, y1: 0,
        x2: x, y2: this.style.lineHeight,
        stroke: this.style.majorTickColor,
        'stroke-width': this.style.majorTickWidth,
        'pointer-events': 'none'
      }));
      const label = utils.formatDate(date, 'Do', tz);
      if (label === '01') {
        result.push(new Text({
          x: x + 2,
          y: this.style.lineHeight / 4,
          cursor: 'default',
          fill: this.style.textColor,
          'font-size': this.style.textSize,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'pointer-events': 'none'
        }, utils.formatDate(date, 'MMM', tz)));
        result.push(new Text({
          x: x + 2,
          y: this.style.lineHeight * 0.75,
          cursor: 'default',
          fill: this.style.textColor,
          'font-size': this.style.textSize,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'pointer-events': 'none'
        }, label));
      } else {
        result.push(new Text({
          x: x + 2,
          y: this.style.lineHeight / 2,
          cursor: 'default',
          fill: this.style.textColor,
          'font-size': this.style.textSize,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'pointer-events': 'none'
        }, label));
      }

      if (tz === 'GMT' || tz === 'UTC') {
        // Force full duration of 24 hours
        date.setTime(date.getTime() + (24 * 60 * 60 * 1000));
      } else {
        // Add 'about' 24 hours, depending on DST transition (e.g. CET/CEST)
        date.setHours(date.getHours() + 24);
      }
    }

    return result;
  }

  /*
  * Tier 1: Months
  * Tier 2: Week start day
  */
  _renderWeeksScale(tz: string) {
    const result = [];

    // Render months
    let date = utils.startOfMonth(this.timeline.loadStart, tz);
    while (date.getTime() <= this.timeline.loadStop.getTime()) {
      const x = this.timeline.positionDate(date);
      result.push(new Line({
        x1: x, y1: 0,
        x2: x, y2: this.style.lineHeight / 2,
        stroke: this.style.majorTickColor,
        'stroke-width': this.style.majorTickWidth,
        'pointer-events': 'none'
      }));
      result.push(new Text({
        x: x + 2,
        y: this.style.lineHeight / 4,
        cursor: 'default',
        fill: this.style.textColor,
        'font-size': this.style.textSize,
        'text-anchor': 'left',
        'dominant-baseline': 'middle',
        'pointer-events': 'none'
      }, utils.formatDate(date, 'MMMM', tz)));

      if (tz === 'GMT' || tz === 'UTC') {
        date.setUTCMonth(date.getUTCMonth() + 1, 1);
      } else {
        date.setMonth(date.getMonth() + 1, 1);
      }
    }

    // Render week starts
    date = utils.startOfWeek(this.timeline.loadStart, tz);
    while (date.getTime() <= this.timeline.loadStop.getTime()) {
      const x = this.timeline.positionDate(date);
      const label = utils.formatDate(date, 'DD', tz) + '/' + utils.formatDate(date, 'MM', tz);
      result.push(new Line({
        x1: x, y1: this.style.lineHeight / 2,
        x2: x, y2: this.style.lineHeight,
        stroke: this.style.majorTickColor,
        'stroke-width': this.style.majorTickWidth,
        'pointer-events': 'none'
      }));

      // Calculate date for next iteration
      if (tz === 'GMT' || tz === 'UTC') {
        date.setUTCDate(date.getUTCDate() + 7);
      } else {
        date.setDate(date.getDate() + 7);
      }

      const x2 = this.timeline.positionDate(date);
      result.push(new Text({
        x: ((x + x2) / 2) + 2,
        y: this.style.lineHeight * 0.75,
        cursor: 'default',
        fill: this.style.textColor,
        'font-size': this.style.textSize,
        'text-anchor': 'middle',
        'dominant-baseline': 'middle',
        'pointer-events': 'none'
      }, label));
    }

    return result;
  }

  _renderWeekDaysScale(tz: string) {
    const result = [];

    // Trunc to a day start before positioning
    const date = utils.startOfWeek(this.timeline.loadStart, tz);

    while (date.getTime() <= this.timeline.loadStop.getTime()) {
      let weekDay;
      let shortYear;
      if (tz === 'GMT' || tz === 'UTC') {
        weekDay = date.getUTCDay();
        shortYear = date.getUTCFullYear() % 100;
      } else {
        weekDay = date.getDay();
        shortYear = date.getFullYear() % 100;
      }
      const x = this.timeline.positionDate(date);
      if (weekDay === 1) { // Monday
        result.push(new Line({
          x1: x, y1: 0,
          x2: x, y2: this.style.lineHeight,
          stroke: this.style.majorTickColor,
          'stroke-width': this.style.majorTickWidth,
          'pointer-events': 'none'
        }));
        result.push(new Text({
          x: x + 2,
          y: this.style.lineHeight / 4,
          cursor: 'default',
          fill: this.style.textColor,
          'font-size': this.style.textSize,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'pointer-events': 'none'
        }, utils.formatDate(date, 'DD', tz) + ' ' + utils.formatDate(date, 'MMM', tz) + ', \'' + shortYear));
      } else {
        result.push(new Line({
          x1: x, y1: this.style.lineHeight / 2,
          x2: x, y2: this.style.lineHeight,
          stroke: this.style.majorTickColor,
          'stroke-width': this.style.majorTickWidth,
          'pointer-events': 'none'
        }));
      }
      const textLabel = utils.formatDate(date, 'dd', tz).charAt(0);

      // Calculate date for next iteration
      if (tz === 'GMT' || tz === 'UTC') {
        // Force full duration of 24 hours
        date.setTime(date.getTime() + (24 * 60 * 60 * 1000));
      } else {
        // Add 'about' 24 hours, depending on DST transition (e.g. CET/CEST)
        date.setHours(date.getHours() + 24);
      }

      const x2 = this.timeline.positionDate(date);
      result.push(new Text({
        x: x + ((x2 - x) / 2),
        y: this.style.lineHeight * 0.75,
        cursor: 'default',
        fill: this.style.textColor,
        'font-size': this.style.textSize,
        'text-anchor': 'middle',
        'dominant-baseline': 'middle',
        'pointer-events': 'none'
      }, textLabel));
    }

    return result;
  }

  _renderMonthsScale(tz: string) {
    const result = [];

    // Trunc to a month start before positioning
    const date = utils.startOfYear(this.timeline.loadStart, tz);

    while (date.getTime() <= this.timeline.loadStop.getTime()) {
      const x = this.timeline.positionDate(date);
      const label = utils.formatDate(date, 'MMM', tz);
      if (label === 'Jan') {
        result.push(new Line({
          x1: x, y1: 0,
          x2: x, y2: this.style.lineHeight,
          stroke: this.style.majorTickColor,
          'stroke-width': this.style.majorTickWidth,
        }));
        result.push(new Text({
          x: x + 2,
          y: this.style.lineHeight / 4,
          cursor: 'default',
          fill: this.style.textColor,
          'font-size': this.style.textSize,
          'text-anchor': 'left',
          'dominant-baseline': 'middle',
          'pointer-events': 'none'
        }, utils.formatDate(date, 'YYYY', tz)));
      } else {
        result.push(new Line({
          x1: x, y1: this.style.lineHeight / 2,
          x2: x, y2: this.style.lineHeight,
          stroke: this.style.majorTickColor,
          'stroke-width': this.style.majorTickWidth,
          'pointer-events': 'none'
        }));
      }

      if (tz === 'GMT' || tz === 'UTC') {
        date.setUTCMonth(date.getUTCMonth() + 1, 1);
      } else {
        date.setMonth(date.getMonth() + 1, 1);
      }

      const x2 = this.timeline.positionDate(date);
      result.push(new Text({
        x: ((x + x2) / 2) + 2,
        y: this.style.lineHeight * 0.75,
        cursor: 'default',
        fill: this.style.textColor,
        'font-size': this.style.textSize,
        'text-anchor': 'middle',
        'dominant-baseline': 'middle',
        'pointer-events': 'none'
      }, label));
    }

    return result;
  }

  onAction(id: string, action: Action) {
    super.onAction(id, action);
    if (id === this.scaleBgId) {
      switch (action.type) {
        case 'click':
          this.timeline.clearSelection();
          break;
        case 'grabstart':
          this.grabX1 = action.date;
          this.timeline.clearSelection();
          break;
        case 'grabmove':
        case 'grabend':
          const grabX2 = action.date;
          this.timeline.selectRange(this.grabX1!, grabX2!);
          break;
      }
    }
  }
}
