import { Parameter } from '@yamcs/client';

export function analyzeStaticValueRanges(parameter: Parameter) {
  let minLow;
  let maxHigh;
  const staticAlarmZones = []; // Disjoint set of OOL alarm zones
  if (parameter.type && parameter.type.defaultAlarm) {
    const defaultAlarm = parameter.type.defaultAlarm;
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
      for (let i = defaultAlarm.staticAlarmRange.length - 1; i >= 0; i--) {
        const range = defaultAlarm.staticAlarmRange[i];
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

  const valueRange: [number|null, number|null] = [null, null]; // Null makes Dygraph choose
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
