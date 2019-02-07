import { Ellipse, G, LinearGradient, Polyline, Rect, Stop, Text } from '../../tags';
import { Bounds } from '../Bounds';
import { Color } from '../Color';
import { Font } from '../Font';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class BooleanButton extends AbstractWidget {

  private squareButton: boolean;
  private showLed: boolean;
  private showBooleanLabel: boolean;

  private effect3d: boolean;
  private onColor: Color;
  private onLabel: string;
  private offColor: Color;
  private offLabel: string;
  private font: Font;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    this.squareButton = utils.parseBooleanChild(this.node, 'square_button');
    this.showLed = utils.parseBooleanChild(this.node, 'show_led');
    this.showBooleanLabel = utils.parseBooleanChild(this.node, 'show_boolean_label');
    this.effect3d = utils.parseBooleanChild(this.node, 'effect_3d');
    const onColorNode = utils.findChild(this.node, 'on_color');
    this.onColor = utils.parseColorChild(onColorNode);
    this.onLabel = utils.parseStringChild(this.node, 'on_label');
    const offColorNode = utils.findChild(this.node, 'off_color');
    this.offColor = utils.parseColorChild(offColorNode);
    this.offLabel = utils.parseStringChild(this.node, 'off_label');
    const fontNode = utils.findChild(this.node, 'font');
    this.font = utils.parseFontNode(fontNode);
  }

  draw(g: G) {
    // Background
    if (this.squareButton) {
      g.addChild(new Rect({
        id: `${this.id}-button`,
        x: this.x,
        y: this.y,
        width: this.width,
        height: this.height,
        fill: Color.DARK_GRAY,
      }));
      if (this.effect3d) {
        let points = `${this.x},${this.y}`;
        points += ` ${this.x + 2},${this.y + 2}`;
        points += ` ${this.x + 2},${this.y + this.height - 2}`;
        points += ` ${this.x},${this.y + this.height}`;
        g.addChild(new Polyline({ points, fill: 'white' }));

        points = `${this.x},${this.y}`;
        points += ` ${this.x + 2},${this.y + 2}`;
        points += ` ${this.x + this.width - 2},${this.y + 2}`;
        points += ` ${this.x + this.width},${this.y}`;
        g.addChild(new Polyline({ points, fill: 'white' }));

        points = `${this.x + this.width},${this.y}`;
        points += ` ${this.x + this.width - 2},${this.y + 2}`;
        points += ` ${this.x + this.width - 2},${this.y + this.height - 2}`;
        points += ` ${this.x + this.width},${this.y + this.height}`;
        g.addChild(new Polyline({ points, fill: Color.DARK_GRAY }));

        points = `${this.x},${this.y + this.height}`;
        points += ` ${this.x + 2},${this.y + this.height - 2}`;
        points += ` ${this.x + this.width - 2},${this.y + this.height - 2}`;
        points += ` ${this.x + this.width},${this.y + this.height}`;
        g.addChild(new Polyline({ points, fill: Color.DARK_GRAY }));
      }
      g.addChild(new Rect({
        x: this.x + 2,
        y: this.y + 2,
        width: this.width - 2 - 2,
        height: this.height - 2 - 2,
        fill: this.backgroundColor,
      }));
    } else {
      if (this.effect3d) {
        const a = this.width / 2;
        const b = this.height / 2;
        const w =  Math.sqrt(a * a + b * b);
        const x1 = a + (b - a - w) / 2 - 1;
        const y1 = b - (b - a + w) / 2 - 1;
        const x2 = a + (b - a + w) / 2 + 5;
        const y2 = b - (b - a - w) / 2 + 5;
        this.display.defs.addChild(new LinearGradient({
          id: `${this.id}-ellipse-bg-g-disabled`,
          x1: `${100 * x1 / this.width}%`,
          y1: `${100 * y1 / this.height}%`,
          x2: `${100 * x2 / this.width}%`,
          y2: `${100 * y2 / this.height}%`,
        }).addChild(
          new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': 1 }),
          new Stop({ offset: '100%', 'stop-color': Color.DARK_GRAY, 'stop-opacity': 1 }),
        ));
        this.display.defs.addChild(new LinearGradient({
          id: `${this.id}-ellipse-bg-g-enabled`,
          x1: `${100 * x1 / this.width}%`,
          y1: `${100 * y1 / this.height}%`,
          x2: `${100 * x2 / this.width}%`,
          y2: `${100 * y2 / this.height}%`,
        }).addChild(
          new Stop({ offset: '0%', 'stop-color': Color.DARK_GRAY, 'stop-opacity': 1 }),
          new Stop({ offset: '100%', 'stop-color': Color.WHITE, 'stop-opacity': 1 }),
        ));
        g.addChild(new Ellipse({
          id: `${this.id}-button`,
          cx: this.x + (this.width / 2),
          cy: this.y + (this.height / 2),
          rx: this.width / 2,
          ry: this.height / 2,
          fill: `url(#${this.id}-ellipse-bg-g-disabled)`,
        }));
      } else {
        g.addChild(new Ellipse({
          id: `${this.id}-button`,
          cx: this.x + (this.width / 2),
          cy: this.y + (this.height / 2),
          rx: this.width / 2,
          ry: this.height / 2,
          fill: Color.DARK_GRAY,
        }));
      }
      g.addChild(new Ellipse({
        cx: this.x + (this.width / 2),
        cy: this.y + (this.height / 2),
        rx: (this.width / 2) - 2,
        ry: (this.height / 2) - 2,
        fill: this.backgroundColor,
      }));
    }

    // Foreground
    if (this.width > this.height) {
      this.drawHorizontal(g);
    } else {
      this.drawVertical(g);
    }

    if (this.showBooleanLabel) {
      g.addChild(new Text({
        x: this.x + (this.width / 2),
        y: this.y + (this.height / 2),
        'pointer-events': 'none',
        ...this.font.getStyle(),
        fill: this.foregroundColor,
        'dominant-baseline': 'middle',
        'text-anchor': 'middle',
      }, this.offLabel));
    }
  }

  private drawHorizontal(g: G) {
    if (this.showLed) {
      let diameter: number;
      if (this.squareButton) {
        diameter = Math.floor(0.3 * (this.width + this.height) / 2);
        if (diameter > Math.min(this.width, this.height)) {
          diameter = Math.min(this.width, this.height) - 2;
        }
      } else {
        diameter = Math.floor(0.25 * (this.width + this.height) / 2);
        if (diameter > Math.min(this.width, this.height)) {
          diameter = Math.min(this.width, this.height) - 8;
        }
      }
      const ledArea: Bounds = {
        x: Math.floor(this.x + this.width * 0.79999 - diameter / 2),
        y: Math.floor(this.y + this.height / 2 - diameter / 2),
        width: diameter,
        height: diameter
      };
      g.addChild(new Ellipse({
        cx: ledArea.x + (ledArea.width / 2),
        cy: ledArea.y + (ledArea.height / 2),
        rx: ledArea.width / 2,
        ry: ledArea.height / 2,
        fill: this.offColor,
      }));
      if (this.effect3d) {
        this.display.defs.addChild(new LinearGradient({
          id: `${this.id}-led-g`,
          x1: '0%', y1: '0%',
          x2: '100%', y2: '100%',
        }).addChild(
          new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '1' }),
          new Stop({ offset: '100%', 'stop-color': this.offColor, 'stop-opacity': '0', }),
        ));
        g.addChild(new Ellipse({
          cx: ledArea.x + (ledArea.width / 2),
          cy: ledArea.y + (ledArea.height / 2),
          rx: ledArea.width / 2,
          ry: ledArea.height / 2,
          fill: `url(#${this.id}-led-g)`,
        }));
      }
    }
  }

  private drawVertical(g: G) {
    if (this.showLed) {
      let diameter: number;
      if (this.squareButton) {
        diameter = Math.floor(0.3 * (this.width + this.height) / 2);
        if (diameter > Math.min(this.width, this.height)) {
          diameter = Math.min(this.width, this.height) - 2;
        }
      } else {
        diameter = Math.floor(0.25 * (this.width + this.height) / 2);
        if (diameter > Math.min(this.width, this.height)) {
          diameter = Math.min(this.width, this.height) - 8;
        }
      }
      const ledArea: Bounds = {
        x: Math.floor(this.x + this.width / 2 - diameter / 2),
        y: Math.floor(this.y + ((1 - 0.79999) * this.height) - diameter / 2),
        width: diameter,
        height: diameter
      };
      g.addChild(new Ellipse({
        cx: ledArea.x + (ledArea.width / 2),
        cy: ledArea.y + (ledArea.height / 2),
        rx: ledArea.width / 2,
        ry: ledArea.height / 2,
        fill: this.offColor,
      }));
      if (this.effect3d) {
        this.display.defs.addChild(new LinearGradient({
          id: `${this.id}-led-g`,
          x1: '0%', y1: '0%',
          x2: '100%', y2: '100%',
        }).addChild(
          new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '1' }),
          new Stop({ offset: '100%', 'stop-color': this.offColor, 'stop-opacity': '0', }),
        ));
        g.addChild(new Ellipse({
          cx: ledArea.x + (ledArea.width / 2),
          cy: ledArea.y + (ledArea.height / 2),
          rx: ledArea.width / 2,
          ry: ledArea.height / 2,
          fill: `url(#${this.id}-led-g)`,
        }));
      }
    }
  }

  afterDomAttachment() {
    const buttonEl = this.svg.getElementById(`${this.id}-button`) as SVGElement;
    buttonEl.addEventListener('click', () => {

    });
  }
}
