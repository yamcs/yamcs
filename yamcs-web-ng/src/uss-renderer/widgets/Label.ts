import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Text, Rect, ClipPath, G } from '../tags';
import { Display } from '../Display';

export class Label extends AbstractWidget {

  constructor(node: Node, display: Display, protected pointerEvents = true) {
    super(node, display);
  }

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
    if (!this.pointerEvents) {
      text.setAttribute('pointer-events', 'none');
    }
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
    let fontSize = Number(text.attributes['font-size']);
    const fontFamily = text.attributes['font-family'];
    if (utils.parseBooleanChild(this.node, 'AutoSize')) {
      fontSize = this.autoscale(innerText, fontFamily, fontSize);
      text.setAttribute('font-size', String(fontSize));
    }

    const fm = this.getFontMetrics(innerText, fontFamily, fontSize);

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

  updateProperty(property: string, value: any, acquisitionStatus: string, monitoringResult: string) {
    console.warn('Unsupported dynamic property: ' + property);
  }

  private autoscale(text: string, fontFamily: string, fontSizeStart: number) {
    const fm = this.getFontMetrics(text, fontFamily, fontSizeStart);
    if (fm.width > this.width || fm.height > this.height) {
      return this.scaleDown(text, fontFamily, fontSizeStart - 1);
    } else {
      return this.scaleUp(text, fontFamily, fontSizeStart);
    }
  }

  private scaleUp(text: string, fontFamily: string, fontSize: number) {
    let fm;
    let size = fontSize;
    while (true) {
      fm = this.getFontMetrics(text, fontFamily, size);
      if (fm.width > this.width || fm.height > this.height) {
        return size - 1;
      } else {
        size++;
      }
    }
  }

  private scaleDown(text: string, fontFamily: string, fontSize: number) {
    let fm;
    let size = fontSize;
    while (true) {
      fm = this.getFontMetrics(text, fontFamily, size);
      if (fm.width <= this.width && fm.height <= this.height) {
        return size;
      } else {
        size--;
      }
    }
  }
}
