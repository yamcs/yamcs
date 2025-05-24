import { FillStyle, Item } from '@fqqb/timeline';
import { TimelineItem } from '@yamcs/webapp-sdk';
import { ActivityItemBackgroundRenderer } from './ActivityItemBackgroundRenderer';
import { ActivityItemData } from './ActivityItemData';
import { ItemBand } from './ItemBand';
import {
  ABORTED_FG,
  CANCELED_FG,
  ERROR_FG,
  PROGRESS_FG,
  SUCCESS_FG,
} from './colors';

const activityBackgroundRenderer = new ActivityItemBackgroundRenderer();

/**
 * An "Activity" item on the Timeline. This contains planned and execution state, and possibly an automated action to be performed.
 */
export class ActivityItem implements Item {
  id?: string | number | undefined;
  start: number;
  stop?: number | undefined;
  label?: string | undefined;
  hoverBackground?: FillStyle | undefined;
  textSize?: number | undefined;
  fontFamily?: string | undefined;
  cornerRadius?: number | undefined;
  data?: any;

  constructor(
    item: Item,
    private band: ItemBand,
  ) {
    this.id = item.id;
    this.start = item.start;
    this.stop = item.stop;
    this.label = item.label;
    this.hoverBackground = item.hoverBackground;
    this.textSize = item.textSize;
    this.fontFamily = item.fontFamily;
    this.cornerRadius = item.cornerRadius;
    this.data = item.data;
  }

  get backgroundRenderer() {
    return activityBackgroundRenderer;
  }

  get textColor() {
    const info = this.data.item as TimelineItem;

    let textColor = '#000000';
    if (info.status === 'SUCCEEDED') {
      textColor = SUCCESS_FG;
    } else if (info.status === 'FAILED') {
      textColor = ERROR_FG;
    } else if (info.status === 'IN_PROGRESS') {
      textColor = PROGRESS_FG;
    } else if (info.status === 'ABORTED') {
      textColor = ABORTED_FG;
    } else if (info.status === 'CANCELED') {
      textColor = CANCELED_FG;
    } else if (info.status === 'SKIPPED') {
      textColor = ERROR_FG;
    }
    return textColor;
  }

  get paddingLeft() {
    const data = this.data as ActivityItemData;
    const { timeline } = this.band.chart;
    const leftMargin = 5;
    if (data.execRange) {
      // Position text at the start of the execution segment
      return (
        timeline.distanceBetween(data.minDate, data.execRange.start) +
        leftMargin
      );
    } else if (data.shiftedPlannedRange) {
      // Position text at the start of the shifted planned segment
      return (
        timeline.distanceBetween(data.minDate, data.shiftedPlannedRange.start) +
        leftMargin
      );
    } else if (data.aheadRange) {
      // Position text at the start of the planned segment
      return (
        timeline.distanceBetween(data.minDate, data.plannedRange.start) +
        leftMargin
      );
    } else {
      return leftMargin;
    }
  }

  get borderWidth() {
    return 0; // Don't interfere with custom background renderer
  }

  onTick(now: number) {
    const data = this.data as ActivityItemData;
    const { item: itemInfo } = data;
    if (itemInfo.status === 'IN_PROGRESS' && data.execRange) {
      data.execRange.stop = now;
      this.start = data.minDate;
      this.stop = data.maxDate;
    }
    if (itemInfo.status === 'READY' && data.delayedRange) {
      data.delayedRange.stop = now;
      this.start = data.minDate;
      this.stop = data.maxDate;
    }
    if (itemInfo.status === 'READY' && data.aheadRange) {
      data.aheadRange.start = now;
      this.start = data.minDate;
      this.stop = data.maxDate;
    }
    if (itemInfo.status === 'READY' && data.shiftedPlannedRange) {
      data.shiftedPlannedRange.start = now;
      data.shiftedPlannedRange.stop = now + data.getPlannedDuration();
    }
  }
}
