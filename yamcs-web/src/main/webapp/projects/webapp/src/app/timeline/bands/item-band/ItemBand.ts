import { Connection, ConnectionType, Item, TimeRange } from '@fqqb/timeline';
import {
  AuthService,
  Formatter,
  TimelineBand,
  TimelineItem,
  utils,
  YaTooltip,
} from '@yamcs/webapp-sdk';
import { TimelineComponent } from '../../timeline.component';
import { TimelineService } from '../../timeline.service';
import { BandBase } from '../BandBase';
import {
  BooleanProperty,
  ColorProperty,
  NumberProperty,
  PropertyInfoSet,
  resolveProperties,
  SelectProperty,
} from '../properties';
import { ActivityItem } from './ActivityItem';
import { ActivityItemData } from './ActivityItemData';

export const propertyInfo: PropertyInfoSet = {
  frozen: new BooleanProperty(false),
  itemBackgroundColor: new ColorProperty('#77b1e1'),
  itemBorderColor: new ColorProperty('#3d94c7'),
  itemBorderWidth: new NumberProperty(1),
  itemCornerRadius: new NumberProperty(0),
  itemHeight: new NumberProperty(20),
  itemMarginLeft: new NumberProperty(5),
  itemTextColor: new ColorProperty('#333333'),
  itemTextOverflow: new SelectProperty<'show' | 'clip' | 'hide'>('show'),
  itemTextSize: new NumberProperty(10),
  marginBottom: new NumberProperty(7),
  marginTop: new NumberProperty(7),
  multiline: new BooleanProperty(true),
  spaceBetweenItems: new NumberProperty(7),
  spaceBetweenLines: new NumberProperty(7),
};

export const itemPropertyInfo: PropertyInfoSet = {
  backgroundColor: new ColorProperty(
    propertyInfo.itemBackgroundColor.defaultValue,
  ),
  borderColor: new ColorProperty(propertyInfo.itemBorderColor.defaultValue),
  borderWidth: new NumberProperty(propertyInfo.itemBorderWidth.defaultValue),
  cornerRadius: new NumberProperty(propertyInfo.itemCornerRadius.defaultValue),
  marginLeft: new NumberProperty(propertyInfo.itemMarginLeft.defaultValue),
  textColor: new ColorProperty(propertyInfo.itemTextColor.defaultValue),
  textSize: new NumberProperty(propertyInfo.itemTextSize.defaultValue),
};

/**
 * Band capable of showing any timeline items (event or activity) as well as
 * dependency connections between them.
 */
export class ItemBand extends BandBase {
  protected timelineService: TimelineService;

  constructor(
    chart: TimelineComponent,
    bandInfo: TimelineBand,
    private formatter: Formatter,
    private tooltipInstance: YaTooltip,
    private authService: AuthService,
  ) {
    super(chart, bandInfo);
    this.timelineService = chart.timelineService;

    this.label = bandInfo.name;
    this.data = { band: bandInfo };

    const properties = resolveProperties(
      propertyInfo,
      bandInfo.properties || {},
    );
    this.frozen = properties.frozen;
    this.itemBackground = properties.itemBackgroundColor;
    this.itemBorderColor = properties.itemBorderColor;
    this.itemBorderWidth = properties.itemBorderWidth;
    this.itemCornerRadius = properties.itemCornerRadius;
    this.itemHeight = properties.itemHeight;
    this.itemPaddingLeft = properties.itemMarginLeft;
    this.itemTextColor = properties.itemTextColor;
    this.itemTextOverflow = properties.itemTextOverflow;
    this.itemTextSize = properties.itemTextSize;
    this.paddingBottom = properties.marginBottom;
    this.paddingTop = properties.marginTop;
    this.multiline = properties.multiline;
    this.spaceBetween = properties.spaceBetweenItems;
    this.lineSpacing = properties.spaceBetweenLines;
    this.connectionLineWidth = 1;
    this.connectionJointRadius = 3;
    this.connectionLineColor = 'rgba(0, 0, 0, 0.1)';

    if (this.mayControlTimeline()) {
      this.setupItemClickListener();
    }
    this.setupTooltip();
  }

  private setupItemClickListener() {
    this.addItemClickListener((evt) => {
      const item = evt.item.data.item as TimelineItem;
      this.timelineService.openEditItemDialog(item);
    });
  }

  private setupTooltip() {
    this.addItemMouseMoveListener((evt) => {
      const data = evt.item.data;
      let ttText = '';

      if (data instanceof ActivityItemData) {
        //const { start, stop } = evt.item;

        const description = data.item.name;
        if (description) {
          ttText += `<strong>${description}</strong><br>`;
        }

        const plannedRange = data.plannedRange;
        if (plannedRange) {
          const { start, stop } = plannedRange;
          ttText += '<br><u>Planned</u><br>';
          ttText += `Start: ${this.formatter.formatDateTime(start, true)}<br>`;
          if (stop) {
            ttText += `Stop : ${this.formatter.formatDateTime(stop, true)}<br>`;
          }
        }

        const execRange = data.execRange;
        if (execRange) {
          const { start, stop } = execRange;
          ttText += '<br><u>Actual</u><br>';
          ttText += `Start: ${this.formatter.formatDateTime(start, true)}<br>`;
          if (stop) {
            ttText += `Stop : ${this.formatter.formatDateTime(stop, true)}<br>`;
          }
        } else {
          ttText += '<br><u>Actual</u><br>';
          ttText += 'n/a';
        }
      } else {
        // Event
        const item = data.item as TimelineItem;
        const { start, stop } = evt.item;

        const description = item.name;
        if (description) {
          ttText += `<strong>${description}</strong><br>`;
        }

        ttText += `Start: ${this.formatter.formatDateTime(start, true)}<br>`;
        if (stop) {
          ttText += `Stop : ${this.formatter.formatDateTime(stop, true)}<br>`;
        }
      }

      this.tooltipInstance.show(ttText, evt.clientX, evt.clientY);
    });

    this.addItemMouseLeaveListener((evt) => {
      this.tooltipInstance.hide();
    });
  }

  override async refreshData(
    loadRange: TimeRange,
    visibleRange: TimeRange,
  ): Promise<void> {
    const page = await this.timelineService.fetchTimelineItems({
      source: 'rdb',
      band: this.bandInfo.id,
      start: new Date(loadRange.start).toISOString(),
      stop: new Date(loadRange.stop).toISOString(),
      details: true,
    });

    const itemInfos = page?.items || [];
    const loadedItems: Item[] = [];
    const loadedConnections: Connection[] = [];
    const visibleItemInfos: TimelineItem[] = [];

    for (const itemInfo of itemInfos) {
      const item = this.toItem(itemInfo);
      loadedItems.push(item);
      if (this.isItemVisible(item, visibleRange)) {
        visibleItemInfos.push(itemInfo);
      }

      if (itemInfo.predecessors) {
        for (const predecessor of itemInfo.predecessors) {
          let type: ConnectionType = 'FINISH_TO_START';
          if (predecessor.startCondition === 'ON_START') {
            type = 'START_TO_START';
          }
          const connection: Connection = {
            from: predecessor.itemId,
            to: itemInfo.id,
            type,
          };
          loadedConnections.push(connection);
        }
      }
    }

    this.items = loadedItems;
    this.connections = loadedConnections;
    this.timelineService.reportItems(this, visibleItemInfos);
  }

  private toItem(itemInfo: TimelineItem): Item {
    if (itemInfo.type === 'ACTIVITY') {
      return this.toActivityItem(itemInfo);
    } else {
      return this.toEventItem(itemInfo);
    }
  }

  private toEventItem(itemInfo: TimelineItem): Item {
    const start = utils.toDate(itemInfo.start).getTime();
    const duration = utils.convertProtoDurationToMillis(itemInfo.duration);
    const item: Item = {
      id: itemInfo.id,
      start,
      stop: duration ? start + duration : undefined,
      label: itemInfo.name,
      data: { item: itemInfo },
    };
    if (itemInfo.properties) {
      this.applyItemOverrides(item, itemInfo.properties);
    }

    return item;
  }

  private toActivityItem(itemInfo: TimelineItem): Item {
    const data = new ActivityItemData(itemInfo);

    let start = data.minDate;
    let stop = data.maxDate;

    const item: Item = {
      id: itemInfo.id,
      start,
      stop,
      label: itemInfo.name,
      data,
    };
    if (itemInfo.properties) {
      this.applyItemOverrides(item, itemInfo.properties);
    }

    return new ActivityItem(item, this);
  }

  private applyItemOverrides(
    item: Item,
    properties: { [key: string]: string },
  ) {
    if ('backgroundColor' in properties) {
      item.background = properties.backgroundColor;
    }
    if ('borderColor' in properties) {
      item.borderColor = properties.borderColor;
    }
    if ('borderWidth' in properties) {
      item.borderWidth = Number(properties.borderWidth);
    }
    if ('cornerRadius' in properties) {
      item.cornerRadius = Number(properties.cornerRadius);
    }
    if ('marginLeft' in properties) {
      item.paddingLeft = Number(properties.marginLeft);
    }
    if ('textColor' in properties) {
      item.textColor = properties.textColor;
    }
    if ('textSize' in properties) {
      item.textSize = Number(properties.textSize);
    }
  }

  private mayControlTimeline() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlTimeline');
  }

  private isItemVisible(item: Item, visibleRange: TimeRange) {
    const { start: visibleStart, stop: visibleStop } = visibleRange;
    if (item.stop) {
      return item.stop > visibleStart && item.start < visibleStop;
    } else {
      return item.start > visibleStart && item.start < visibleStop;
    }
  }

  override onTick(now: number): void {
    for (const item of this.items) {
      if (item instanceof ActivityItem) {
        item.onTick(now);
      }
    }
  }

  override disconnectedCallback(): void {
    this.timelineService.releaseItems(this);
  }
}
