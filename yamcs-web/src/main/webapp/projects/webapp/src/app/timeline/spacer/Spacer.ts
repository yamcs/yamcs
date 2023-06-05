import { Banner } from '@fqqb/timeline';
import { TimelineBand } from '@yamcs/webapp-sdk';
import { TimelineChartPage } from '../TimelineChartPage';
import { NumberProperty, PropertyInfoSet, resolveProperties } from '../properties';

export const propertyInfo: PropertyInfoSet = {
  height: new NumberProperty(34),
};

export class Spacer extends Banner {

  constructor(chart: TimelineChartPage, bandInfo: TimelineBand) {
    super(chart.timeline);

    this.label = bandInfo.name;
    this.data = { band: bandInfo };

    const properties = resolveProperties(propertyInfo, bandInfo.properties || {});
    this.contentHeight = properties.height;
  }
}
