import { Component, Input, OnInit } from '@angular/core';
import { Parameter } from '@yamcs/client';
import { AlarmZone } from './AlarmZone';

@Component({
  selector: 'app-parameter-series',
  template: '',
})
export class ParameterSeries implements OnInit {

  @Input()
  parameter: Parameter;

  @Input()
  grid = false;

  @Input()
  axis = true;

  @Input()
  axisLineWidth = 1;

  @Input()
  alarmRanges: 'line' | 'fill' | 'none' = 'line';

  /**
   * Disjoint set of OOL alarm zones
   */
  public staticAlarmZones: AlarmZone[] = [];
  public minLow?: number;
  public maxHigh?: number;

  ngOnInit() {
    if (this.parameter.type && this.parameter.type.defaultAlarm) {
      const defaultAlarm = this.parameter.type.defaultAlarm;
      if (defaultAlarm.staticAlarmRange) {
        let last_y = -Infinity;

        // LOW LIMITS
        for (let i = defaultAlarm.staticAlarmRange.length - 1; i >= 0; i--) {
          const range = defaultAlarm.staticAlarmRange[i];
          if (range.minInclusive !== undefined) {
            const zone = {
              y1: last_y,
              y2: range.minInclusive,
              y1IsLimit: false,
              color: this.colorForLevel(range.level) || 'black',
            };
            this.staticAlarmZones.push(zone);
            last_y = zone.y2;

            if (this.minLow === undefined) {
              this.minLow = range.minInclusive;
            } else {
              this.minLow = Math.min(this.minLow, range.minInclusive);
            }
          }
        }

        // HIGH LIMITS
        last_y = Infinity;
        for (let i = defaultAlarm.staticAlarmRange.length - 1; i >= 0; i--) {
          const range = defaultAlarm.staticAlarmRange[i];
          if (range.maxInclusive) {
            const zone = {
              y1: range.maxInclusive,
              y2: last_y,
              y1IsLimit: true,
              color: this.colorForLevel(range.level) || 'black',
            };
            this.staticAlarmZones.push(zone);
            last_y = zone.y1;

            if (this.maxHigh === undefined) {
              this.maxHigh = range.maxInclusive;
            } else {
              this.maxHigh = Math.max(this.maxHigh, range.maxInclusive);
            }
          }
        }
      }
    }
  }

  public getStaticValueRange() {
    const valueRange: [number|null, number|null] = [null, null]; // Null makes Dygraph choose
    if (this.minLow !== undefined) {
      valueRange[0] = this.minLow;
    }
    if (this.maxHigh !== undefined) {
      valueRange[1] = this.maxHigh;
    }
    return valueRange;
  }

  private colorForLevel(level: string) {
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
