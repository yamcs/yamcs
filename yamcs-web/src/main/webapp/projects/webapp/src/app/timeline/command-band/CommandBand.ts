import { ItemBand } from '@fqqb/timeline';
import { TimelineBand } from '@yamcs/webapp-sdk';
import { TimelineChartComponent } from '../timeline-chart/timeline-chart.component';

export class CommandBand extends ItemBand {
  constructor(chart: TimelineChartComponent, bandInfo: TimelineBand) {
    super(chart.timeline);
    this.label = bandInfo.name;
    this.paddingBottom = 7;
    this.paddingTop = 7;
    this.lineSpacing = 2;
    this.itemHeight = 20;
    this.data = { band: bandInfo };
  }
}
