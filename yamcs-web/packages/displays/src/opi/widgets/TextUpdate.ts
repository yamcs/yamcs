import { G, Rect, Text } from '../../tags';
import { Font } from '../Font';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class TextUpdate extends AbstractWidget {

  private font: Font;

  constructor(node: Element, display: OpiDisplay) {
    super(node, display);
    const fontNode = utils.findChild(this.node, 'font');
    this.font = utils.parseFontNode(fontNode);
  }

  draw(g: G) {
    const wrapperG = new G({
      transform: `translate(${this.x},${this.y})`,
    });
    g.addChild(wrapperG);

    const bgRect = new Rect({
      x: 0,
      y: 0,
      width: this.width,
      height: this.height,
      fill: this.backgroundColor,
    });
    if (this.transparent) {
      bgRect.setAttribute('fill-opacity', '0');
    }
    wrapperG.addChild(bgRect);

    const fm = this.getFontMetrics('i', this.font);

    const text = new Text({
      x: 0,
      y: 0,
      'pointer-events': 'none',
      ...this.font.getStyle(),
      fill: this.foregroundColor,
    }, this.text);

    wrapperG.addChild(text);

    let x;
    const horizAlignment = utils.parseIntChild(this.node, 'horizontal_alignment');
    if (horizAlignment === 0) { // LEFT
      x = 0;
      text.setAttribute('text-anchor', 'start');
    } else if (horizAlignment === 1) { // CENTER
      x = 0 + this.width / 2;
      text.setAttribute('text-anchor', 'middle');
    } else if (horizAlignment === 2) { // RIGHT
      x = 0 + this.width;
      text.setAttribute('text-anchor', 'end');
    }
    text.setAttribute('x', String(x));

    let y;
    const vertAlignment = utils.parseIntChild(this.node, 'vertical_alignment');
    if (vertAlignment === 0) { // TOP
      y = Math.ceil(fm.height / 2);
    } else if (vertAlignment === 1) { // MIDDLE
      y = Math.ceil(this.height / 2);
    } else if (vertAlignment === 2) { // BOTTOM
      y = Math.ceil(this.height - (fm.height / 2));
    }
    text.setAttribute('dominant-baseline', 'middle');
    text.setAttribute('y', String(y));
  }
}
