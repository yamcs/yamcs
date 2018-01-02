import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';

export class Rectangle extends AbstractWidget {

  parseAndDraw(svg: any, parent: any, node: Node) {
    const settings = {
      ...utils.parseFillStyle(node),
      ...utils.parseDrawStyle(node)
    };
    if ('strokeWidth' in settings) {
      this.x += 0.5;
      this.y += 0.5;
    }
    svg.rect(parent, this.x, this.y, this.width, this.height, settings);
  }
}
