import { G, Rect, Text, Tspan } from '../../tags';
import { Color } from '../Color';
import { AbstractWidget } from './AbstractWidget';

export class ActionButton extends AbstractWidget {

  brightStroke: Color;
  darkStroke: Color;

  parseAndDraw() {
    const g = new G({
      class: 'action-button',
      'data-name': this.name,
      cursor: 'pointer',
    });

    const strokeWidth = 3;
    const boxWidth = this.width - strokeWidth;
    const boxHeight = this.height - strokeWidth;

    this.brightStroke = this.backgroundColor.brighter().brighter();
    this.darkStroke = this.backgroundColor.darker();

    g.addChild(new Rect({
      fill: this.backgroundColor,
      stroke: this.brightStroke,
      'stroke-width': strokeWidth,
      'stroke-opacity': 1,
      'shape-rendering': 'crispEdges',
    }).withBorderBox(this.x, this.y, this.width, this.height));

    // Cheap shade effect
    g.addChild(new Rect({
      fill: 'transparent',
      'shape-rendering': 'crispEdges',
      stroke: this.darkStroke,
      'stroke-width': strokeWidth,
      'stroke-opacity': 1,
      'stroke-dasharray': `0,${boxWidth},${boxWidth + boxHeight},${boxHeight}`,
    }).withBorderBox(this.x, this.y, this.width, this.height));

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
      ...this.textStyle,
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

    return g;
  }

  afterDomAttachment() {
    const textEl = this.svg.getElementById(`${this.id}-text`) as SVGTextElement;
    const bbox = textEl.getBBox();

    const gEl = this.svg.getElementById(`${this.id}-textg`) as SVGGElement;
    const x = this.x + (this.width - bbox.width) / 2;
    const y = this.y + (this.height - bbox.height) / 2;
    gEl.setAttribute('transform', `translate(${x} ${y})`);
  }
}
