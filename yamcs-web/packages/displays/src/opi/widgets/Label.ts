import { G, Rect, Text, Tspan } from '../../tags';
import { Font } from '../Font';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class Label extends AbstractWidget {

  private font: Font;
  private horizAlignment: number;
  private vertAlignment: number;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    const fontNode = utils.findChild(this.node, 'font');
    this.font = utils.parseFontNode(fontNode);

    this.horizAlignment = utils.parseIntChild(this.node, 'horizontal_alignment');
    this.vertAlignment = utils.parseIntChild(this.node, 'vertical_alignment');
  }

  draw(g: G) {
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

    const textG = new G({
      id: `${this.id}-textg`,
      transform: `translate(${this.x} ${this.y})`
    });

    const lines = this.text.split('\n');

    const text = new Text({
      id: `${this.id}-text`,
      x: 0,
      y: 0,
      'pointer-events': 'none',
      ...this.font.getStyle(),
      fill: this.foregroundColor,
    });

    for (let i = 0; i < lines.length; i++) {
      text.addChild(new Tspan({
        x: 0,
        dy: (i === 0) ? '0' : '1.2em',
        'dominant-baseline': 'hanging',
      }, lines[i]));
    }

    textG.addChild(text);
    g.addChild(textG);
  }

  afterDomAttachment() {
    const textEl = this.svg.getElementById(`${this.id}-text`) as SVGTextElement;
    console.log('Search for ', this);
    if (!textEl) {
      console.log('OOPS', this);
      return;
    }
    const bbox = textEl.getBBox();

    const gEl = this.svg.getElementById(`${this.id}-textg`) as SVGGElement;

    let x = this.x;
    if (this.horizAlignment === 0) { // LEFT
      x = this.x;
    } else if (this.horizAlignment === 1) { // CENTER
      x = this.x + (this.width - bbox.width) / 2;
    } else if (this.horizAlignment === 2) { // RIGHT
      x = this.x + (this.width - bbox.width);
    }

    let y = this.y;
    if (this.vertAlignment === 0) { // TOP
      y = this.y;
    } else if (this.vertAlignment === 1) { // MIDDLE
      y = this.y + ((this.height - bbox.height) / 2);
    } else if (this.vertAlignment === 2) { // BOTTOM
      y = this.y + (this.height - bbox.height);
    }

    gEl.setAttribute('transform', `translate(${x} ${y})`);
  }
}
