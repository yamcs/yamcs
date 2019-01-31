import { G, Rect, Text, Tspan } from '../../tags';
import { Font } from '../Font';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class TextInput extends AbstractWidget {

  private font: Font;
  private horizAlignment: number;

  constructor(node: Element, display: OpiDisplay) {
    super(node, display);
    const fontNode = utils.findChild(this.node, 'font');
    this.font = utils.parseFontNode(fontNode);

    this.horizAlignment = utils.parseIntChild(this.node, 'horizontal_alignment');
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

    const y = this.y + ((this.height - bbox.height) / 2);
    gEl.setAttribute('transform', `translate(${x} ${y})`);
  }
}
