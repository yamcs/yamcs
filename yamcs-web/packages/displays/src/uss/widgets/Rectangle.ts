import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Rect } from '../../tags';
import { DataSourceBinding } from '../DataSourceBinding';

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

  registerBinding(binding: DataSourceBinding) {
    console.warn('Unsupported dynamic property: ' + binding.dynamicProperty);
  }
}
