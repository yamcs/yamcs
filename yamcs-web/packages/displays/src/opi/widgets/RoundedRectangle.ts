import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { Rectangle } from './Rectangle';

export class RoundedRectangle extends Rectangle {

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    this.cornerWidth = utils.parseIntChild(node, 'corner_width');
    this.cornerHeight = utils.parseIntChild(node, 'corner_height');
  }
}
