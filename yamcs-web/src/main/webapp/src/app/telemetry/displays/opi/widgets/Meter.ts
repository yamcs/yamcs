import { G, Rect } from '../../tags';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class Meter extends AbstractWidget {

  private minimum: Number;
  private maximum: Number;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    this.minimum = utils.parseFloatChild(node, 'minimum');
    this.maximum = utils.parseFloatChild(node, 'maximum');
  }

  draw(g: G) {
    g.addChild(new Rect({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      fill: this.backgroundColor,
    }));
  }
}
