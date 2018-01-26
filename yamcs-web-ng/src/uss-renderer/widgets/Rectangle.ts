import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Rect } from '../tags';

export class Rectangle extends AbstractWidget {

  parseAndDraw() {
    const settings = {
      ...utils.parseFillStyle(this.node),
      ...utils.parseDrawStyle(this.node),
    };

    return new Rect({
      ...settings,
      class: 'rectangle',
      'data-name': this.name,
      'shape-rendering': 'crispEdges',
    }).withBorderBox(this.x, this.y, this.width, this.height);
  }

  updateProperty(property: string, value: any, acquisitionStatus: string, monitoringResult: string) {
    console.warn('Unsupported dynamic property: ' + property);
  }
}
