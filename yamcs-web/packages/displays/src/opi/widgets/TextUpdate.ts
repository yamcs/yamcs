import { G, Rect, Text } from '../../tags';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class TextUpdate extends AbstractWidget {

  parseAndDraw() {
    const g = new G({
      id: `${this.id}-group`,
      transform: `translate(${this.x},${this.y})`,
      class: 'text-update',
      'data-name': this.name,
    });

    const bgRect = new Rect({
      x: 0,
      y: 0,
      width: this.width,
      height: this.height,
      fill: this.backgroundColor,
      ...this.borderStyle,
      'shape-rendering': 'crispEdges',
    });
    if (this.transparent) {
      bgRect.setAttribute('fill-opacity', '0');
    }
    g.addChild(bgRect);

    const fontFamily = this.textStyle['font-family'];
    const fontSize = this.textStyle['font-size'];
    const fontStyle = this.textStyle['font-style'] || 'normal';
    const fontWeight = this.textStyle['font-weight'] || 'normal';
    const fm = this.getFontMetrics('i', fontFamily, fontStyle, fontWeight, fontSize);

    const text = new Text({
      x: 0,
      y: 0,
      'pointer-events': 'none',
      ...this.textStyle,
      fill: this.foregroundColor,
    }, this.text);

    g.addChild(text);

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

    return g;
  }
}
