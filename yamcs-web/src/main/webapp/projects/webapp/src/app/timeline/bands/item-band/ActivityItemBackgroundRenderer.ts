import {
  AnnotatedItem,
  Bounds,
  DefaultItemBackgroundRenderer,
  FillStyle,
  Graphics,
  ItemBand,
} from '@fqqb/timeline';
import { ActivityItemData } from './ActivityItemData';
import {
  ABORTED_BG,
  ABORTED_FG,
  CANCELED_BG,
  CANCELED_FG,
  ERROR_BG,
  ERROR_FG,
  PROGRESS_BG,
  PROGRESS_FG,
  SUCCESS_BG,
  SUCCESS_FG,
} from './colors';
import { inProgressPattern, plannedPattern, skippedPattern } from './patterns';

/**
 * Overrides the drawing of the item's background and border.
 *
 * Because an item can have multiple ranges (planned, pending, executing), at the level
 * of the rendering library we register the item with a min/max that may span multiple
 * of these ranges (for example: both a pending period and an execution period).
 *
 * Then with this renderer we render these ranges differently, and shift the label
 * to the correct range by adding some left padding.
 */
export class ActivityItemBackgroundRenderer extends DefaultItemBackgroundRenderer {
  override drawBackground(
    g: Graphics,
    band: ItemBand,
    item: AnnotatedItem,
    bounds: Bounds,
    hovered: boolean,
  ): void {
    if (bounds.width <= band.itemMinWidth) {
      this.drawMinWidthBackground(g, band, item, bounds, hovered);
    } else {
      this.drawMultiSegmentBackground(g, band, item, bounds, hovered);
    }
  }

  private drawMinWidthBackground(
    g: Graphics,
    band: ItemBand,
    item: AnnotatedItem,
    bounds: Bounds,
    hovered: boolean,
  ) {
    const data = item.data as ActivityItemData;
    const { item: itemInfo } = data;

    let rawX1 = bounds.x;
    let rawX2 = bounds.x + bounds.width;

    let x1 = Math.floor(rawX1) + 0.5;
    let x2 = Math.floor(rawX2) + 0.5;

    const vMargin = 0;
    const y = Math.floor(bounds.y + vMargin) + 0.5;
    let width = x2 - x1;
    const height = Math.floor(bounds.height - vMargin * 2) - 1;

    let background: FillStyle = '#ffffff';
    let border = '#000000';
    if (itemInfo.status === 'SUCCEEDED') {
      background = SUCCESS_BG;
      border = SUCCESS_FG;
    } else if (itemInfo.status === 'FAILED') {
      background = ERROR_BG;
      border = ERROR_FG;
    } else if (itemInfo.status === 'IN_PROGRESS') {
      background = PROGRESS_BG;
      border = PROGRESS_FG;
    } else if (itemInfo.status === 'ABORTED') {
      background = ABORTED_BG;
      border = ABORTED_FG;
    } else if (itemInfo.status === 'CANCELED') {
      background = CANCELED_BG;
      border = CANCELED_FG;
    }

    g.fillRect({
      x: x1,
      y,
      width,
      height,
      fill: background,
    });

    g.strokeRect({
      x: x1,
      y,
      width,
      height,
      color: border,
    });
  }

  private drawMultiSegmentBackground(
    g: Graphics,
    band: ItemBand,
    item: AnnotatedItem,
    bounds: Bounds,
    hovered: boolean,
  ) {
    const data = item.data as ActivityItemData;
    const {
      item: itemInfo,
      plannedRange,
      delayedRange,
      aheadRange,
      shiftedPlannedRange,
      execRange,
    } = data;

    let estimatedExecRange = shiftedPlannedRange ?? plannedRange;
    if (estimatedExecRange && data.showPlannedRange()) {
      const rawX1 = this.positionDate(data, bounds, estimatedExecRange.start);
      const rawX2 = this.positionDate(data, bounds, estimatedExecRange.stop);

      const x1 = Math.floor(rawX1) + 0.5;
      const x2 = Math.floor(rawX2) + 0.5;

      const vMargin = 0;
      const y = Math.floor(bounds.y + vMargin) + 0.5;
      const width = x2 - x1;
      const height = Math.floor(bounds.height - vMargin * 2) - 1;

      if (data.item.status === 'IN_PROGRESS') {
        g.fillRect({
          x: x1,
          y,
          width,
          height,
          fill: inProgressPattern,
        });
        g.strokeRect({
          x: x1,
          y,
          width,
          height,
          color: PROGRESS_FG,
          dash: [4, 4],
        });
      } else if (
        data.item.status === 'SKIPPED' ||
        data.item.status === 'CANCELED' ||
        data.item.status === 'ABORTED'
      ) {
        g.fillRect({
          x: x1,
          y,
          width,
          height,
          fill: skippedPattern,
        });
        g.strokeRect({
          x: x1,
          y,
          width,
          height,
          color: ERROR_FG,
          dash: [4, 4],
        });
      } else {
        g.fillRect({
          x: x1,
          y,
          width,
          height,
          fill: plannedPattern,
        });
        g.strokeRect({
          x: x1,
          y,
          width,
          height,
          color: '#000000',
          dash: [4, 4],
        });
      }
    }

    if (delayedRange) {
      const rawX1 = this.positionDate(data, bounds, delayedRange.start);
      const rawX2 = this.positionDate(data, bounds, delayedRange.stop);

      const x1 = Math.floor(rawX1) + 0.5;
      const x2 = Math.floor(rawX2) + 0.5;

      const vMargin = 6;
      const y = Math.floor(bounds.y + vMargin) + 0.5;
      const width = x2 - x1;
      const height = Math.floor(bounds.height - vMargin * 2) - 1;

      g.fillRect({
        x: x1,
        y,
        width,
        height,
        fill: 'black',
      });
      g.strokeRect({
        x: x1,
        y,
        width,
        height,
        color: 'black',
      });
    }

    if (aheadRange) {
      const rawX1 = this.positionDate(data, bounds, aheadRange.start);
      const rawX2 = this.positionDate(data, bounds, aheadRange.stop);

      const x1 = Math.floor(rawX1) + 0.5;
      const x2 = Math.floor(rawX2) + 0.5;

      const vMargin = 6;
      const y = Math.floor(bounds.y + vMargin) + 0.5;
      const width = x2 - x1;
      const height = Math.floor(bounds.height - vMargin * 2) - 1;

      g.fillRect({
        x: x1,
        y,
        width,
        height,
        fill: 'white',
      });
      g.strokeRect({
        x: x1,
        y,
        width,
        height,
        color: 'black',
      });
    }

    if (execRange) {
      let rawX1 = this.positionDate(data, bounds, execRange.start);
      let rawX2 = this.positionDate(data, bounds, execRange.stop);

      let x1 = Math.floor(rawX1) + 0.5;
      let x2 = Math.floor(rawX2) + 0.5;

      const vMargin = 0;
      const y = Math.floor(bounds.y + vMargin) + 0.5;
      let width = Math.max(x2 - x1, band.itemMinWidth);
      const height = Math.floor(bounds.height - vMargin * 2) - 1;

      let background: FillStyle = '#ffffff';
      let border = '#000000';
      if (itemInfo.status === 'SUCCEEDED') {
        background = SUCCESS_BG;
        border = SUCCESS_FG;
      } else if (itemInfo.status === 'FAILED') {
        background = ERROR_BG;
        border = ERROR_FG;
      } else if (itemInfo.status === 'IN_PROGRESS') {
        background = PROGRESS_BG;
        border = PROGRESS_FG;
      } else if (itemInfo.status === 'ABORTED') {
        background = ABORTED_BG;
        border = ABORTED_FG;
      } else if (itemInfo.status === 'CANCELED') {
        background = CANCELED_BG;
        border = CANCELED_FG;
      }

      g.fillRect({
        x: x1,
        y,
        width,
        height,
        fill: background,
      });

      // Have stroke cover both the execRange and the shiftedPlannedRange
      const start = Math.min(
        shiftedPlannedRange?.start ?? execRange.start,
        execRange.start,
      );
      const stop = Math.max(
        shiftedPlannedRange?.stop ?? execRange.stop,
        execRange.stop,
      );
      rawX1 = this.positionDate(data, bounds, start);
      rawX2 = this.positionDate(data, bounds, stop);
      x1 = Math.floor(rawX1) + 0.5;
      x2 = Math.floor(rawX2) + 0.5;
      width = Math.max(x2 - x1, band.itemMinWidth);

      g.strokeRect({
        x: x1,
        y,
        width,
        height,
        color: border,
      });
    }
  }

  private positionDate(item: ActivityItemData, bounds: Bounds, date: number) {
    const { minDate, maxDate } = item;
    const x1 = bounds.x;
    const x2 = x1 + bounds.width;
    const progress = (date - minDate) / (maxDate - minDate);
    return x1 + (x2 - x1) * progress;
  }
}
