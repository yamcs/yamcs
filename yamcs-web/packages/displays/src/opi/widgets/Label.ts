import { G, Rect, Text } from '../../tags';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class Label extends AbstractWidget {

  parseAndDraw(g: G) {
    const rect = new Rect({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      fill: this.backgroundColor.toString(),
      'pointer-events': 'none',
    });
    if (this.transparent) {
      rect.setAttribute('fill-opacity', '0');
    }
    g.addChild(rect);

    const text = new Text({
      x: this.x,
      y: this.y,
      'pointer-events': 'none',
      ...this.textStyle,
      fill: this.foregroundColor,
    }, this.text);
    g.addChild(text);

    let x;
    const horizAlignment = utils.parseIntChild(this.node, 'horizontal_alignment');
    if (horizAlignment === 0) { // LEFT
      x = this.x;
      text.setAttribute('text-anchor', 'start');
    } else if (horizAlignment === 1) { // CENTER
      x = this.x + this.width / 2;
      text.setAttribute('text-anchor', 'middle');
    } else if (horizAlignment === 2) { // RIGHT
      x = this.x + this.width;
      text.setAttribute('text-anchor', 'end');
    }
    text.setAttribute('x', String(x));

    const fontSize = text.attributes['font-size'];
    const fontFamily = text.attributes['font-family'];
    const fontStyle = text.attributes['font-style'] || 'normal';
    const fontWeight = text.attributes['font-weight'] || 'normal';

    const fm = this.getFontMetrics(this.text, fontFamily, fontStyle, fontWeight, fontSize);

    let y;
    const vertAlignment = utils.parseIntChild(this.node, 'vertical_alignment');
    if (vertAlignment === 0) { // TOP
      y = this.y + (fm.height / 2);
    } else if (vertAlignment === 1) { // MIDDLE
      y = this.y + (this.height / 2);
    } else if (vertAlignment === 2) { // BOTTOM
      y = this.y + this.height - (fm.height / 2);
    }
    text.setAttribute('dominant-baseline', 'middle');
    text.setAttribute('y', String(y));

    return g;
  }
}
