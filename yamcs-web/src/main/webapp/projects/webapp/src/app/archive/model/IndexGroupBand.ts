import { DefaultSidebar, Item, ItemBand, Timeline } from '@fqqb/timeline';
import { Formatter, utils } from '@yamcs/webapp-sdk';
import { TimelineTooltipComponent } from '../timeline-tooltip/timeline-tooltip.component';
import { ArchiveRecordGroup } from './ArchiveRecordGroup';
import { RGB } from './RGB';

export const PADDING_TB = 2;

export class IndexGroupBand extends ItemBand {
  private backgroundRGB: RGB;
  private foregroundRGB: RGB;
  private formatter: Formatter;

  constructor(
    timeline: Timeline,
    label: string,
    backgroundColor: RGB,
    foregroundColor: RGB,
    formatter: Formatter,
  ) {
    super(timeline);
    this.label = label;
    this.borderWidth = 0;
    this.multiline = false;
    this.itemBorderColor = 'rgba(0, 0, 0, 0.1)';
    this.itemBorderWidth = 1;
    this.itemCornerRadius = 0;
    this.itemTextOverflow = 'hide';
    this.itemTextSize = 10;
    this.itemHeight = 14;
    this.paddingBottom = PADDING_TB;
    this.paddingTop = PADDING_TB;
    this.backgroundRGB = backgroundColor;
    this.itemBackground = this.backgroundRGB.toCssString();
    this.foregroundRGB = foregroundColor;
    this.itemTextColor = this.foregroundRGB.toCssString();
    this.formatter = formatter;
    this.background = 'white';

    this.addHeaderMouseEnterListener((ev) => {
      this.background = (timeline.sidebar! as DefaultSidebar).hoverOverlayColor;
    });
    this.addHeaderMouseLeaveListener((ev) => {
      this.background = 'white';
    });

    this.addItemClickListener((clickEvent) => {
      const { start, stop } = clickEvent.item;
      if (start && stop) {
        this.timeline.setSelection(start, stop);
      } else {
        this.timeline.clearSelection();
      }
    });
  }

  setupTooltip(tooltipInstance: TimelineTooltipComponent) {
    this.addItemMouseMoveListener((evt) => {
      const { start, stop, data } = evt.item;
      let ttText = data.name + '\n';
      ttText += `Start: ${this.formatter.formatDateTime(start, true)}\n`;
      ttText += `Stop : ${this.formatter.formatDateTime(stop!, true)}\n`;
      if (data.count >= 0) {
        const sec = (stop! - start) / 1000;
        ttText += `Count: ${data.count}`;
        const hz = data.count / sec;
        if (hz >= 1000) {
          ttText += ` (${(hz / 1000).toFixed(3)} kHz)`;
        } else if (hz > 1) {
          ttText += ` (${hz.toFixed(3)} Hz)`;
        }
      } else if (data.description) {
        ttText += data.description;
      }
      tooltipInstance.show(ttText, evt.clientX, evt.clientY);
    });

    this.addItemMouseLeaveListener((evt) => {
      tooltipInstance.hide();
    });
  }

  loadData(group: ArchiveRecordGroup) {
    const items: Item[] = [];
    for (const record of group.records) {
      const start = utils.toDate(record.first).getTime();
      const stop = utils.toDate(record.last).getTime();
      const item: Item = {
        start,
        stop,
        data: {
          name: group.name,
          count: record.num,
        },
      };
      if (record.num > 1) {
        const sec = (stop - start) / 1000;
        const hz = record.num / sec;
        if (hz >= 1000) {
          item.label = `${(hz / 1000).toFixed(1)} kHz`;
        } else if (hz >= 0.1) {
          item.label = `${hz.toFixed(1)} Hz`;
        } else {
          item.label = '< 0.1 Hz';
        }

        if (hz >= 1) {
          item.background = this.backgroundRGB.toCssString(1);
        } else if (hz >= 0.3) {
          item.background = this.backgroundRGB.toCssString(hz);
        } else {
          item.background = this.backgroundRGB.toCssString(0.3);
        }
      }

      items.push(item);
    }
    this.items = items;
  }
}
