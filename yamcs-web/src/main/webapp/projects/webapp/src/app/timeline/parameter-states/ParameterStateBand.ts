import { Overlay } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { ElementRef } from '@angular/core';
import { Item, ItemBand } from '@fqqb/timeline';
import {
  BackfillingSubscription,
  ConfigService,
  Formatter,
  GetParameterRangesOptions,
  ParameterSubscription,
  Range,
  Synchronizer,
  TimelineBand,
  utils,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import {
  BooleanProperty,
  ColorProperty,
  NumberProperty,
  PropertyInfoSet,
  resolveProperties,
  TextProperty,
} from '../shared/properties';
import { TimelineChartComponent } from '../timeline-chart/timeline-chart.component';
import { ParameterStatesTooltipComponent } from './parameter-states-tooltip/parameter-states-tooltip.component';
import { State } from './State';
import { StateBuffer } from './StateBuffer';
import { StateLegend } from './StateLegend';
import { StateRemapper } from './StateRemapper';

/**
 * Distance between two states, before inserting gap
 */
const MAX_GAP = 120000;

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

export function resolveValueMappingProperties(
  index: number,
  info: PropertyInfoSet,
  properties: { [key: string]: any },
) {
  const prefix = `value_mapping_${index}_`;
  const prefixedResult = resolveProperties(info, properties);
  const lstripped: { [key: string]: any } = {};
  for (const key in prefixedResult) {
    if (prefixedResult[key] !== '') {
      lstripped[key.slice(prefix.length)] = prefixedResult[key];
    }
  }
  return lstripped;
}

export class ParameterStateBand extends ItemBand {
  private tooltipInstance: ParameterStatesTooltipComponent;
  private backfillSubscription?: BackfillingSubscription;

  private parameter: string;
  private stateRemapper = new StateRemapper();
  private legend = new StateLegend();

  private mixedPattern: CanvasPattern;

  private stateBuffer: StateBuffer;
  private realtimeSubscription: ParameterSubscription;
  private syncSubscription: Subscription;

  constructor(
    chart: TimelineChartComponent,
    bandInfo: TimelineBand,
    private yamcs: YamcsService,
    synchronizer: Synchronizer,
    private formatter: Formatter,
    private configService: ConfigService,
    overlay: Overlay,
  ) {
    super(chart.timeline);
    this.label = bandInfo.name;
    this.multiline = false;
    this.itemTextOverflow = 'hide';
    this.itemHoverBorderWidth = 1;
    this.data = { band: bandInfo };

    const properties = resolveProperties(
      propertyInfo,
      bandInfo.properties || {},
    );
    this.frozen = properties.frozen ?? propertyInfo.frozen.defaultValue;
    this.itemHeight = properties.height ?? propertyInfo.height.defaultValue;
    this.parameter =
      properties.parameter ?? propertyInfo.parameter.defaultValue;

    let idx = 0;
    while (true) {
      const mappingPropertiesInfo = createValueMappingPropertyInfo(idx);
      const mappingProperties = resolveValueMappingProperties(
        idx,
        mappingPropertiesInfo,
        bandInfo.properties || {},
      );
      if (!mappingProperties.type) {
        break;
      }
      idx++;
      this.stateRemapper.addMapping(mappingProperties);
    }

    const bodyRef = new ElementRef(document.body);
    const positionStrategy = overlay
      .position()
      .flexibleConnectedTo(bodyRef)
      .withPositions([
        {
          originX: 'start',
          originY: 'top',
          overlayX: 'start',
          overlayY: 'top',
        },
      ])
      .withPush(false);

    const overlayRef = overlay.create({ positionStrategy });
    const tooltipPortal = new ComponentPortal(ParameterStatesTooltipComponent);
    this.tooltipInstance = overlayRef.attach(tooltipPortal).instance;

    this.stateBuffer = new StateBuffer(
      MAX_GAP,
      this.formatter,
      this.stateRemapper,
      () => {
        this.refreshData(false /* don't reset color assignment */);
      },
    );
    this.syncSubscription = synchronizer.sync(() => {
      this.updateChart();
    });

    this.backfillSubscription = yamcs.yamcsClient.createBackfillingSubscription(
      {
        instance: yamcs.instance!,
      },
      (update) => {
        if (update.finished) {
          this.refreshData(false /* don't reset color assignment */);
        }
      },
    );

    this.addItemMouseEnterListener((evt) => {
      this.tooltipInstance.show(evt.clientX, evt.clientY, this.legend);
    });
    this.addItemMouseMoveListener((evt) => {
      this.tooltipInstance.show(
        evt.clientX,
        evt.clientY,
        this.legend,
        evt.item,
      );
    });
    this.addItemMouseLeaveListener((evt) => {
      this.tooltipInstance.hide();
    });

    this.mixedPattern = this.createMixedPattern();
  }

  refreshData(resetColors = true) {
    // Load some offscreen data to reduce likelihood of missing ranges that start
    // offscreen, yet end in the viewport.
    const offscreenEdge = (this.timeline.stop - this.timeline.start) / 10;
    const loadStart = this.timeline.start - offscreenEdge;
    const loadStop = this.timeline.stop;
    const maxRanges = 5000;
    const options: GetParameterRangesOptions = {
      // maxValues can be high, states are reprocessed with a
      // lower maxValues value in StateRemapper (then also
      // accounting for realtime values)
      maxValues: 15,
      maxGap: MAX_GAP,
      start: new Date(loadStart).toISOString(),
      stop: new Date(loadStop).toISOString(),
      minRange: Math.floor((loadStop - loadStart) / maxRanges),
    };

    if (this.configService.getConfig().tmArchive) {
      this.yamcs.yamcsClient
        .getParameterRanges(this.yamcs.instance!, this.parameter, options)
        .then((ranges) => {
          this.connectRealtime();
          const states = this.convertRangesToStates(ranges);
          this.stateBuffer.reset();
          this.stateBuffer.setArchiveData(states);
        })
        .catch((err) => {
          console.warn(`Failed to retrieve samples for ${this.parameter}`, err);
          this.stateBuffer.reset();
          this.stateBuffer.setArchiveData([]);
        })
        .finally(() => {
          if (resetColors) {
            this.legend.resetColorAssignment();
          }
          // Quick emit, don't wait on sync tick
          this.updateChart();
        });
    } else {
      this.connectRealtime();
      this.stateBuffer.reset();
      this.stateBuffer.setArchiveData([]);
      if (resetColors) {
        this.legend.resetColorAssignment();
      }
      // Quick emit, don't wait on sync tick
      this.updateChart();
    }
  }

  private connectRealtime() {
    this.realtimeSubscription?.cancel();
    this.realtimeSubscription =
      this.yamcs.yamcsClient.createParameterSubscription(
        {
          instance: this.yamcs.instance!,
          processor: this.yamcs.processor!,
          id: [{ name: this.parameter }],
          sendFromCache: false,
          updateOnExpiration: false, // TODO turn into gap
          abortOnInvalid: true,
          action: 'REPLACE',
        },
        (data) => {
          for (const pval of data.values || []) {
            this.stateBuffer.addRealtimeValue(pval);
          }
        },
      );
  }

  private convertRangesToStates(ranges: Range[]): State[] {
    const states: State[] = [];
    for (const range of ranges) {
      const state: State = {
        start: utils.toDate(range.start).getTime(),
        stop: utils.toDate(range.stop).getTime(),
        values: [],
        otherCount: range.otherCount,
        mixed: false, // Calculated later on
        mostFrequentValue: { value: null, count: 0 }, // Calculated later on
      };

      for (let i = 0; i < range.engValues?.length; i++) {
        const textValue = this.formatter.formatValue(range.engValues[i]);
        const count = range.counts[i];
        state.values.push({ value: textValue, count });
      }

      states.push(state);
    }

    return states;
  }

  private updateChart() {
    const states = this.stateBuffer.snapshot(
      this.timeline.start,
      this.timeline.stop,
    );
    this.legend.recalculate(states);

    const items: Item[] = [];
    for (const state of states) {
      let background: string | CanvasPattern;
      let textColor: string;
      let label: string;
      if (state.mixed) {
        background = this.mixedPattern;
        textColor = '#000';
        label = 'Mixed';
      } else if (state.otherCount > 0) {
        background = '#000';
        textColor = '#fff';
        label = 'Misc';
      } else {
        background = this.legend.getBackground(state);
        textColor = this.legend.getForeground(state);
        label = this.legend.getLabel(state);
      }

      const item: Item = {
        start: state.start,
        stop: state.stop,
        background,
        textColor,
        label,
        borderWidth: 0,
        data: { range: state },
      };
      items.push(item);
    }

    this.items = items;
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
    ctx.moveTo((size * 2) / 3, 0);
    ctx.lineTo(size, 0);
    ctx.lineTo(size, (size * 1) / 3);
    ctx.lineTo((size * 1) / 3, size);
    ctx.lineTo(0, size);
    ctx.lineTo(0, (size * 2) / 3);
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
    this.realtimeSubscription?.cancel();
    this.syncSubscription?.unsubscribe();
  }
}
