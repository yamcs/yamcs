import { Banner } from '@fqqb/timeline';
import { TimelineBand } from '@yamcs/webapp-sdk';
import { NumberProperty, PropertyInfoSet, resolveProperties } from '../shared/properties';
import { TimelineChartComponent } from '../timeline-chart/timeline-chart.component';

export const propertyInfo: PropertyInfoSet = {
  height: new NumberProperty(34),
};

export class Spacer extends Banner {

  constructor(chart: TimelineChartComponent, bandInfo: TimelineBand) {
    super(chart.timeline);

    this.label = bandInfo.name;
    this.data = { band: bandInfo };

    const properties = resolveProperties(propertyInfo, bandInfo.properties || {});
    this.contentHeight = properties.height;
  }
}
