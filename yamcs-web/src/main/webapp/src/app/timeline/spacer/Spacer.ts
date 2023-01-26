import { Banner } from '@fqqb/timeline';
import { TimelineBand } from '../../client/types/timeline';
import { NumberProperty, PropertyInfoSet, resolveProperties } from '../properties';
import { TimelineChartPage } from '../TimelineChartPage';

export const propertyInfo: PropertyInfoSet = {
  height: new NumberProperty(34),
}

export class Spacer extends Banner {

  constructor(chart: TimelineChartPage, bandInfo: TimelineBand) {
    super(chart.timeline);

    this.label = bandInfo.name;
    this.data = { band: bandInfo };

    const properties = resolveProperties(propertyInfo, bandInfo.properties || {});
    this.contentHeight = properties.height;
  }
}
