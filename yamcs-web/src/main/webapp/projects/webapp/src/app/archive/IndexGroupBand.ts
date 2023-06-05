import { Item, ItemBand, Timeline } from '@fqqb/timeline';
import { DateTimePipe, IndexGroup, utils } from '@yamcs/webapp-sdk';
import { TimelineTooltip } from './TimelineTooltip';

export class IndexGroupBand extends ItemBand {

  constructor(timeline: Timeline, label: string) {
    super(timeline);
    this.label = label;
    this.borderWidth = 0;
    this.multiline = false;
    this.itemBorderColor = 'rgba(0, 0, 0, 0.1)';
    this.itemBorderWidth = 1;
    this.itemCornerRadius = 0;
    this.itemTextOverflow = 'hide';
    this.itemHeight = 20;

    this.addItemClickListener(clickEvent => {
      const { start, stop } = clickEvent.item;
      if (start && stop) {
        this.timeline.setSelection(start, stop);
      } else {
        this.timeline.clearSelection();
      }
    });
  }

  setupTooltip(tooltipInstance: TimelineTooltip, dateTimePipe: DateTimePipe) {
    this.addItemMouseMoveListener(evt => {
      const { start, stop, data } = evt.item;
      let ttText = data.name + '<br>';
      ttText += `Start: ${dateTimePipe.transform(new Date(start))}<br>`;
      ttText += `Stop:&nbsp; ${dateTimePipe.transform(new Date(stop!))}<br>`;
      if (data.count >= 0) {
        const sec = (stop! - start) / 1000;
        ttText += `Count: ${data.count}`;
        if (data.count > 1) {
          ttText += ` (${(data.count / sec).toFixed(3)} Hz)`;
        }
      } else if (data.description) {
        ttText += data.description;
      }
      tooltipInstance.show(ttText, evt.clientX, evt.clientY);
    });

    this.addItemMouseLeaveListener(evt => {
      tooltipInstance.hide();
    });
  }

  loadData(group: IndexGroup) {
    const items: Item[] = [];
    for (const entry of group.entry) {
      const start = utils.toDate(entry.start).getTime();
      const stop = utils.toDate(entry.stop).getTime();
      const item: Item = {
        start, stop, data: {
          name: group.id.name,
          count: entry.count,
        }
      };
      if (entry.count > 1) {
        const sec = (stop - start) / 1000;
        item.label = `${(entry.count / sec).toFixed(1)} Hz`;
      }
      items.push(item);
    }
    this.items = items;
  }
}
