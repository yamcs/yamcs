import { ItemBand } from '@fqqb/timeline';
import { TimelineBand } from '@yamcs/webapp-sdk';
import { TimelineChartPage } from '../TimelineChartPage';

export class CommandBand extends ItemBand {

  constructor(chart: TimelineChartPage, bandInfo: TimelineBand) {
    super(chart.timeline);
    this.label = bandInfo.name;
    this.marginBottom = 7;
    this.marginTop = 7;
    this.lineSpacing = 2;
    this.itemHeight = 20;
    this.data = { band: bandInfo };
  }
}
