import { AbstractWidget } from './AbstractWidget';
import * as utils from '../utils';

export class ExternalImage extends AbstractWidget {

  parseAndDraw(svg: any, parent: any, e: Node) {
    const pathname = utils.parseStringChild(e, 'Pathname');
    svg.image(parent, this.x, this.y, this.width, this.height, '/_static/' + pathname);
  }
}
