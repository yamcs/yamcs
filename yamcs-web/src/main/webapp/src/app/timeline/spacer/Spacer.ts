import { Banner } from '@fqqb/timeline';
import { TimelineBand } from '../../client/types/timeline';
import { TimelineChartPage } from '../TimelineChartPage';
import { addDefaultSpacerProperties } from './SpacerStyles';

export class Spacer extends Banner {

  constructor(chart: TimelineChartPage, bandInfo: TimelineBand) {
    super(chart.timeline);

    this.label = bandInfo.name;
    this.data = { band: bandInfo };

    const properties = addDefaultSpacerProperties(bandInfo.properties || {});
    this.contentHeight = properties.height;
  }
}
