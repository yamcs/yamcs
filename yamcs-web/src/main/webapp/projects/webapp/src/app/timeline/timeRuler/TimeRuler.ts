import { TimeRuler as DefaultTimeRuler } from '@fqqb/timeline';
import { TimelineBand } from '@yamcs/webapp-sdk';
import { TimelineChartPage } from '../TimelineChartPage';

export class TimeRuler extends DefaultTimeRuler {

  constructor(chart: TimelineChartPage, bandInfo: TimelineBand) {
    super(chart.timeline);
    this.contentHeight = 20;
    this.label = bandInfo.name;
    this.timezone = bandInfo.properties!.timezone;
    this.data = { band: bandInfo };
  }
}
