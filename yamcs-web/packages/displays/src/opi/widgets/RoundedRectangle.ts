import { G, Rect } from '../../tags';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class RoundedRectangle extends AbstractWidget {

  private cornerWidth: number;
  private cornerHeight: number;

  constructor(node: Element, display: OpiDisplay) {
    super(node, display);
    this.cornerWidth = utils.parseIntChild(node, 'corner_width');
    this.cornerHeight = utils.parseIntChild(node, 'corner_height');
  }

  draw(g: G) {
    const rect = new Rect({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      rx: this.cornerWidth / 2,
      ry: this.cornerHeight / 2,
      fill: this.backgroundColor,
    });
    if (this.transparent) {
      rect.setAttribute('fill-opacity', '0');
    }
    g.addChild(rect);
  }
}
