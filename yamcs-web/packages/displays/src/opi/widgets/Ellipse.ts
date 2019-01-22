import { Ellipse as EllipseTag, G } from '../../tags';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class Ellipse extends AbstractWidget {

  private alpha: number;

  constructor(node: Element, display: OpiDisplay) {
    super(node, display);
    this.alpha = utils.parseIntChild(node, 'alpha');
  }

  draw(g: G) {
    let opacity = this.alpha / 255;
    if (this.transparent) {
      opacity = 0;
    }

    g.addChild(new EllipseTag({
      cx: this.x + (this.width / 2),
      cy: this.y + (this.height / 2),
      rx: this.width / 2,
      ry: this.height / 2,
      fill: this.backgroundColor,
      'fill-opacity': opacity,
    }));
  }
}
