import { NamedParameterType } from './NamedParameterType';

export type CustomBarsValue = [number, number, number] | null;

/**
 * Sample for a time-based plot.
 * http://dygraphs.com/data.html#array
 */
export type DySample = [Date, CustomBarsValue]
  | [Date, CustomBarsValue, CustomBarsValue]
  | [Date, CustomBarsValue, CustomBarsValue, CustomBarsValue]
  | [Date, CustomBarsValue, CustomBarsValue, CustomBarsValue, CustomBarsValue];

export type DySeries = DySample[];

/**
 * Annotation for a sample on a time-based plot.
 */
export type DyAnnotation = {
  series: string;
  x: number;
  shortText?: string;
  text?: string;
  icon?: string;
  width?: number;
  height?: number;
  cssClass?: string;
  tickHeight?: number;
  tickWidth?: number;
  tickColor?: string;
  attachAtBottom?: boolean;
};

export type DyLegendData = {
  series: DyLegendSeries[];
  x: number;
  xHTML: string;
};

export type DyLegendSeries = {
  color: string;
  dashHTML: string;
  isVisible: boolean;
  label: string;
  labelHTML: string;
  y: number;
  yHTML: string;
};

export type TimestampTrackerData = {
  timestamp: Date;
  canvasx: number;
};

/**
 * A range of data (which does not overlap with any other guideline)
 */
export interface AlarmZone {
  y1: number;
  y2: number;
  y1IsLimit: boolean;
  color: string;
}

export function analyzeStaticValueRanges(parameter: NamedParameterType) {
  let minLow;
  let maxHigh;
  const staticAlarmZones = []; // Disjoint set of OOL alarm zones
  if (parameter.type && parameter.type.defaultAlarm) {
    const defaultAlarm = parameter.type.defaultAlarm;
    if (defaultAlarm.staticAlarmRanges) {
      let last_y = -Infinity;

      // LOW LIMITS
      for (let i = defaultAlarm.staticAlarmRanges.length - 1; i >= 0; i--) {
        const range = defaultAlarm.staticAlarmRanges[i];
        if (range.minInclusive !== undefined) {
          const zone = {
            y1: last_y,
            y2: range.minInclusive,
            y1IsLimit: false,
            color: colorForLevel(range.level) || 'black',
          };
          staticAlarmZones.push(zone);
          last_y = zone.y2;

          if (minLow === undefined) {
            minLow = range.minInclusive;
          } else {
            minLow = Math.min(minLow, range.minInclusive);
          }
        }
      }

      // HIGH LIMITS
      last_y = Infinity;
      for (let i = defaultAlarm.staticAlarmRanges.length - 1; i >= 0; i--) {
        const range = defaultAlarm.staticAlarmRanges[i];
        if (range.maxInclusive) {
          const zone = {
            y1: range.maxInclusive,
            y2: last_y,
            y1IsLimit: true,
            color: colorForLevel(range.level) || 'black',
          };
          staticAlarmZones.push(zone);
          last_y = zone.y1;

          if (maxHigh === undefined) {
            maxHigh = range.maxInclusive;
          } else {
            maxHigh = Math.max(maxHigh, range.maxInclusive);
          }
        }
      }
    }
  }

  const valueRange: [number | null, number | null] = [null, null]; // Null makes Dygraph choose
  if (minLow !== undefined) {
    valueRange[0] = minLow;
  }
  if (maxHigh !== undefined) {
    valueRange[1] = maxHigh;
  }
  return { valueRange, staticAlarmZones };
}

function colorForLevel(level: string) {
  switch (level) {
    case 'WATCH': return '#ffdddb';
    case 'WARNING': return '#ffc3c1';
    case 'DISTRESS': return '#ffaaa8';
    case 'CRITICAL': return '#c35e5c';
    case 'SEVERE': return '#a94442';
    default: console.error('Unknown level ' + level);
  }
}
