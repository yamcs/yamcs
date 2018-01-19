import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Text, Rect, ClipPath, G } from '../tags';

export class BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

export class Label extends AbstractWidget {

  parseAndDraw() {
    const g = new G({
      class: 'label',
      'data-name': this.name,
    });
    let innerText = utils.parseStringChild(this.node, 'Text');
    innerText = innerText.split(' ').join('\u00a0'); // Preserve whitespace

    const textStyleNode = utils.findChild(this.node, 'TextStyle');

    // Clip text within the defined boundary.
    // Should be exceptional because USS makes it really hard to make Labels
    // smaller than its content.
    // TODO clip-path (nor -webkit-clip-path) does not work on Safari
    const clipId = this.generateChildId();
    g.addChild(new ClipPath({ id: clipId }).addChild(
      new Rect({
        x: this.x,
        y: this.y,
        width: this.width,
        height: this.height,
      })
    ));

    const text = new Text({
      id: this.id,
      y: this.y,
      ...utils.parseTextStyle(textStyleNode),
      'clip-path': `url(#${clipId})`,
    }, innerText);
    g.addChild(text);

    let x;
    const horizAlignment = utils.parseStringChild(textStyleNode, 'HorizontalAlignment');
    if (horizAlignment === 'CENTER') {
      x = this.x + this.width / 2;
      text.setAttribute('text-anchor', 'middle');
    } else if (horizAlignment === 'LEFT') {
      x = this.x;
      text.setAttribute('text-anchor', 'start');
    } else if (horizAlignment === 'RIGHT') {
      x = this.x + this.width;
      text.setAttribute('text-anchor', 'end');
    }
    text.setAttribute('x', String(x));

    // Prefer FontMetrics over baseline tricks to account for
    // ascends and descends.
    const fontSize = Number(text.attributes['font-size']);
    const fm = this.getFontMetrics(innerText, fontSize);

    const vertAlignment = utils.parseStringChild(textStyleNode, 'VerticalAlignment');
    if (vertAlignment === 'CENTER') {
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('y', String(this.y + (this.height / 2)));
    } else if (vertAlignment === 'TOP') {
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('y', String(this.y + (fm.height / 2)));
    } else if (vertAlignment === 'BOTTOM') {
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('y', String(this.y + this.height - (fm.height / 2)));
    }

    return g;
  }
}
