import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Rect } from '../tags';

export class Rectangle extends AbstractWidget {

  parseAndDraw() {
    const settings = {
      ...utils.parseFillStyle(this.node),
      ...utils.parseDrawStyle(this.node),
    };

    if ('stroke-width' in settings) {
      this.x += 0.5;
      this.y += 0.5;
    }
    return new Rect({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      ...settings,
      class: 'rectangle',
      'data-name': this.name,
    });
  }
}
