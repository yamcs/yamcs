import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { ElementRef } from '@angular/core';
import { Item, ItemBand } from '@fqqb/timeline';
import { BackfillingSubscription, Formatter, GetParameterRangesOptions, MessageService, Range, TimelineBand, utils, YamcsService } from '@yamcs/webapp-sdk';
import { BooleanProperty, ColorProperty, NumberProperty, PropertyInfoSet, resolveProperties, TextProperty } from '../shared/properties';
import { TimelineChartComponent } from '../timeline-chart/timeline-chart.component';
import { ColorMap } from './ColorMap';
import { ParameterStatesTooltipComponent } from './parameter-states-tooltip/parameter-states-tooltip.component';

export const propertyInfo: PropertyInfoSet = {
  frozen: new BooleanProperty(false),
  height: new NumberProperty(30),
  parameter: new TextProperty(''),
};

export function createValueMappingPropertyInfo(index: number): PropertyInfoSet {
  const set: PropertyInfoSet = {};
  set[`value_mapping_${index}_type`] = new TextProperty('');
  set[`value_mapping_${index}_value`] = new TextProperty('');
  set[`value_mapping_${index}_start`] = new NumberProperty();
  set[`value_mapping_${index}_end`] = new NumberProperty();
  set[`value_mapping_${index}_label`] = new TextProperty('');
  set[`value_mapping_${index}_color`] = new ColorProperty('');
  return set;
}

export function resolveValueMappingProperties(index: number, info: PropertyInfoSet, properties: { [key: string]: any; }) {
  const prefix = `value_mapping_${index}_`;
  const prefixedResult = resolveProperties(info, properties);
  const lstripped: { [key: string]: any; } = {};
  for (const key in prefixedResult) {
    if (prefixedResult[key] !== '') {
      lstripped[key.slice(prefix.length)] = prefixedResult[key];
    }
  }
  return lstripped;
}

interface CountedValue {
  value: string | null;
  count: number;
  // Preferred color
  color?: string;
}

export interface State {

  start: number;

  stop: number;

  // Preprocessed values (mappings applied)
  values: CountedValue[];

  // The most frequent value in a range
  mostFrequentValue: CountedValue;

  otherCount: number;

  // If true, different values fall within this range
  mixed: boolean;
}

export class ParameterStates extends ItemBand {

  private tooltipInstance: ParameterStatesTooltipComponent;
  private backfillSubscription?: BackfillingSubscription;

  private parameter: string;
  private colorMap = new ColorMap();
  private legend = new Map<string, string | CanvasPattern>();

  private mappings: Array<{ [key: string]: any; }> = [];
  private mixedPattern: CanvasPattern;

  constructor(
    chart: TimelineChartComponent,
    bandInfo: TimelineBand,
    private yamcs: YamcsService,
    private messageService: MessageService,
    private formatter: Formatter,
    overlay: Overlay,
  ) {
    super(chart.timeline);
    this.label = bandInfo.name;
    this.multiline = false;
    this.itemTextOverflow = 'hide';
    this.itemHoverBorderWidth = 1;

    this.data = { band: bandInfo };

    const properties = resolveProperties(propertyInfo, bandInfo.properties || {});
    this.frozen = properties.frozen ?? propertyInfo.frozen.defaultValue;
    this.itemHeight = properties.height ?? propertyInfo.height.defaultValue;
    this.parameter = properties.parameter ?? propertyInfo.parameter.defaultValue;

    let idx = 0;
    while (true) {
      const mappingPropertiesInfo = createValueMappingPropertyInfo(idx);
      const mappingProperties = resolveValueMappingProperties(idx, mappingPropertiesInfo, bandInfo.properties || {});
      if (!mappingProperties.type) {
        break;
      }
      idx++;
      this.mappings.push(mappingProperties);
    }

    const bodyRef = new ElementRef(document.body);
    const positionStrategy = overlay.position()
      .flexibleConnectedTo(bodyRef)
      .withPositions([{
        originX: 'start',
        originY: 'top',
        overlayX: 'start',
        overlayY: 'top',
      }])
      .withPush(false);

    const overlayRef = overlay.create({ positionStrategy });
    const tooltipPortal = new ComponentPortal(ParameterStatesTooltipComponent);
    this.tooltipInstance = overlayRef.attach(tooltipPortal).instance;

    this.backfillSubscription = yamcs.yamcsClient.createBackfillingSubscription({
      instance: yamcs.instance!
    }, update => {
      if (update.finished) {
        this.refreshData();
      }
    });

    this.addItemMouseEnterListener(evt => {
      this.tooltipInstance.show(evt.clientX, evt.clientY, this.legend);
    });
    this.addItemMouseMoveListener(evt => {
      this.tooltipInstance.show(evt.clientX, evt.clientY, this.legend, evt.item);
    });
    this.addItemMouseLeaveListener(evt => {
      this.tooltipInstance.hide();
    });

    this.mixedPattern = this.createMixedPattern();
  }

  refreshData() {
    const loadStart = this.timeline.start;
    const loadStop = this.timeline.stop;
    const maxRanges = 5000;
    const options: GetParameterRangesOptions = {
      maxValues: 5,
      maxGap: 120000, // Insert gap of two minutes without data
      start: new Date(loadStart).toISOString(),
      stop: new Date(loadStop).toISOString(),
      minRange: Math.floor((loadStop - loadStart) / maxRanges),
    };

    this.yamcs.yamcsClient.getParameterRanges(this.yamcs.instance!, this.parameter, options)
      .then(ranges => {
        const states = this.preprocess(ranges);
        this.populateTimeline(states);
      })
      .catch(err => {
        console.warn(`Failed to retrieve samples for ${this.parameter}`, err);
        this.populateTimeline([]);
      });
  }

  private preprocess(ranges: Range[]): State[] {
    const result: State[] = [];

    let prevState: State | null = null;
    for (const range of ranges) {
      let state: State = {
        start: utils.toDate(range.start).getTime(),
        stop: utils.toDate(range.stop).getTime(),
        values: [],
        mixed: false,
        mostFrequentValue: { value: null, count: 0 },
        otherCount: 0,
      };
      let valueTotal = 0;
      for (let i = 0; i < range.engValues?.length; i++) {
        valueTotal += range.counts[i];
        let value = this.formatter.formatValue(range.engValues[i]);
        let color: string | undefined = undefined;
        for (const mapping of this.mappings) {
          if (mapping.type === 'value') {
            if (value === mapping.value) {
              if (mapping.label) {
                value = mapping.label;
              }
              color = mapping.color || undefined;
            }
          } else if (mapping.type === 'range') {
            const start = Number(mapping.start);
            const end = Number(mapping.end);
            if (!isNaN(value as any)) {
              const numberValue = Number(value);
              if (numberValue >= start && numberValue <= end) {
                if (mapping.label) {
                  value = mapping.label;
                }
                color = mapping.color || undefined;
              }
            }
          }
        }

        // If there were mappings applied, we may need to combine some values together
        let prevMatch: CountedValue | undefined = undefined;
        if (this.mappings.length) {
          for (const candidate of state.values) {
            if (candidate.value === value) {
              prevMatch = candidate;
            }
          }
        }
        if (prevMatch) {
          prevMatch.count += range.counts[i];
        } else {
          state.values.push({ value, color, count: range.counts[i] });
        }
      }
      state.otherCount = range.count - valueTotal;

      // With all the mappings, we can now perhaps merge with the previous state
      // (follow server behavior: merge if the same set of values appear).
      if (prevState && this.canMerge(prevState, state)) {
        prevState.otherCount += state.otherCount;
        prevState.stop = state.stop;
        for (const v of state.values) {
          let prev = prevState.values.find(x => x.value === v.value);
          if (prev) {
            prev.count += v.count;
          } else {
            prevState.values.push(v);
          }
        }
      } else {
        result.push(state);
        prevState = state;
      }
    }

    // With all merges done, do a 2nd pass
    for (const state of result) {
      // Determine the most frequent value
      let mostFrequentNonOther: CountedValue = { value: null, count: 0 };
      let distinctValues = 0;
      for (const valueCount of state.values) {
        if (valueCount.count > mostFrequentNonOther.count) {
          mostFrequentNonOther = { ...valueCount };
        }
        if (valueCount.count > 0) {
          distinctValues++;
        }
      }

      let mostFrequent: CountedValue;
      if (state.otherCount > mostFrequentNonOther.count) {
        mostFrequent = { value: '__OTHER', count: state.otherCount };
      } else {
        mostFrequent = { ...mostFrequentNonOther };
      }
      if (state.otherCount > 0) {
        distinctValues = Infinity;
      }
      state.mostFrequentValue = mostFrequent;
      state.mixed = distinctValues > 1;
    }

    return result;
  }

  private canMerge(a: State, b: State) {
    if ((a.otherCount === 0 && b.otherCount !== 0) || (a.otherCount !== 0 && b.otherCount === 0)) {
      return false;
    }
    if (a.stop !== b.start) {
      return false;
    }
    const aVals = a.values.filter(countedValue => countedValue.count > 0).map(countedValue => countedValue.value).sort();
    const bVals = b.values.filter(countedValue => countedValue.count > 0).map(countedValue => countedValue.value).sort();
    if (aVals.length !== bVals.length) {
      return false;
    }
    for (let i = 0; i < aVals.length; i++) {
      if (aVals[i] !== bVals[i]) {
        return false;
      }
    }
    return true;
  }

  private populateTimeline(states: State[]) {
    const legendMap = new Map<string, string>();
    for (const state of states) {
      for (const stateValue of state.values) {
        if (!legendMap.has(stateValue.value!)) {
          legendMap.set(stateValue.value!, stateValue.color ?? this.colorMap.colorForValue(stateValue.value));
        }
      }
    }
    const sorted = [...legendMap.entries()].sort((a, b) => {
      return a[0].localeCompare(b[0], undefined, {
        numeric: true,
        sensitivity: 'base',
      });
    });
    this.legend.clear();
    for (const [value, color] of sorted) {
      this.legend.set(value, color);
    }
    this.legend.set('__OTHER', this.colorMap.colorForValue('__OTHER'));

    const items: Item[] = [];
    for (const state of states) {
      const backgroundColor = state.mostFrequentValue.color ?? this.colorMap.colorForValue(state.mostFrequentValue.value);
      const item: Item = {
        start: state.start,
        stop: state.stop,
        background: state.mixed ? this.mixedPattern : backgroundColor,
        textColor: !state.mixed && this.isDark(backgroundColor) ? '#fff' : '#000',
        borderWidth: 0,
        label: state.mixed ? 'Mixed' : (state.mostFrequentValue.value ?? 'null'),
        data: { range: state },
      };
      items.push(item);
    }

    this.items = items;
  }

  private isDark(hexColor: string) {
    const color = (hexColor.charAt(0) === '#') ? hexColor.substring(1, 7) : hexColor;
    const r = parseInt(color.substring(0, 2), 16);
    const g = parseInt(color.substring(2, 4), 16);
    const b = parseInt(color.substring(4, 6), 16);
    return ((r * 0.299) + (g * 0.587) + (b * 0.114)) <= 186;
  }

  private createMixedPattern() {
    const size = 6;
    const offscreen = document.createElement('canvas');
    offscreen.width = 6;
    offscreen.height = 6;
    const ctx = offscreen.getContext('2d')!;
    ctx.fillStyle = '#d3d3d3';

    // Top triangle
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(size / 3, 0);
    ctx.lineTo(0, size / 3);
    ctx.closePath();
    ctx.fill();

    // Diagonal
    ctx.beginPath();
    ctx.moveTo(size * 2 / 3, 0);
    ctx.lineTo(size, 0);
    ctx.lineTo(size, size * 1 / 3);
    ctx.lineTo(size * 1 / 3, size);
    ctx.lineTo(0, size);
    ctx.lineTo(0, size * 2 / 3);
    ctx.closePath();
    ctx.fill();

    // Bottom triangle
    ctx.beginPath();
    ctx.moveTo(size, size);
    ctx.lineTo(size, size - size / 3);
    ctx.lineTo(size - size / 3, size);
    ctx.closePath();
    ctx.fill();

    return ctx.createPattern(offscreen, 'repeat')!;
  }

  override disconnectedCallback(): void {
    this.backfillSubscription?.cancel();
  }
}
