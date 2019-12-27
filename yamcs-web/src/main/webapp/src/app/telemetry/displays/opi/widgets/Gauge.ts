import { Ellipse, Ellipse as EllipseTag, G, LinearGradient, Stop } from '../../tags';
import { Bounds } from '../Bounds';
import { Color } from '../Color';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

const BORDER_COLOR = new Color(100, 100, 100);
const NEEDLE_DIAMETER = 16;

export class Gauge extends AbstractWidget {

  private effect3d: boolean;
  private minimum: Number;
  private maximum: Number;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    this.effect3d = utils.parseBooleanChild(node, 'effect_3d');
    this.minimum = utils.parseFloatChild(node, 'minimum');
    this.maximum = utils.parseFloatChild(node, 'maximum');
  }

  draw(g: G) {
    const width = Math.min(this.width, this.height);
    const height = width;
    this.drawBackground(g, width, height);
    this.drawNeedleCenter(g, width, height);
  }

  private drawBackground(g: G, width: number, height: number) {
    g.addChild(new EllipseTag({
      cx: this.x + (width / 2),
      cy: this.y + (height / 2),
      rx: width / 2,
      ry: height / 2,
      fill: Color.GRAY,
    }));
    if (this.effect3d) {
      this.display.defs.addChild(new LinearGradient({
        id: `${this.id}-bg-grad`,
        x1: '0%', y1: '0%',
        x2: '100%', y2: '100%',
      }).addChild(
        new Stop({ offset: '0%', 'stop-color': BORDER_COLOR }),
        new Stop({ offset: '100%', 'stop-color': Color.WHITE }),
      ));
      g.addChild(new EllipseTag({
        cx: this.x + (width / 2),
        cy: this.y + (height / 2),
        rx: width / 2,
        ry: height / 2,
        fill: `url(#${this.id}-bg-grad)`,
      }));
    }

    const strokeWidth = this.effect3d ? 2 : 1;
    g.addChild(new EllipseTag({
      cx: this.x + (width / 2),
      cy: this.y + (height / 2),
      rx: width / 2 - strokeWidth,
      ry: height / 2 - strokeWidth,
      fill: this.backgroundColor,
    }));
    if (this.effect3d) {
      const R = width / 2;
      const UD_FILL_PART = 9.5 / 10;
      const UP_DOWN_RATIO = 1 / 2;
      const LR_FILL_PART = 8.5 / 10;
      const UP_ANGLE = 0 * Math.PI / 180;
      const DOWN_ANGLE = 35 * Math.PI / 180;
      const glossy1Bounds: Bounds = {
        x: Math.floor(this.x + width / 2 - R * LR_FILL_PART * Math.cos(UP_ANGLE)),
        y: Math.floor(this.y + height / 2 - R * UD_FILL_PART),
        width: Math.floor(2 * R * LR_FILL_PART * Math.cos(UP_ANGLE)),
        height: Math.floor(R * UD_FILL_PART + R * UP_DOWN_RATIO),
      };
      this.display.defs.addChild(new LinearGradient({
        id: `${this.id}-bg-glossy1`,
        x1: '0%', y1: '0%',
        x2: '0%', y2: '100%',
      }).addChild(
        new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': 90 / 255 }),
        new Stop({ offset: '100%', 'stop-color': Color.WHITE, 'stop-opacity': 0 }),
      ));
      g.addChild(new Ellipse({
        cx: glossy1Bounds.x + (glossy1Bounds.width / 2),
        cy: glossy1Bounds.y + (glossy1Bounds.height / 2),
        rx: glossy1Bounds.width / 2,
        ry: glossy1Bounds.height / 2,
        fill: `url(#${this.id}-bg-glossy1)`,
      }));
      const glossy2Bounds: Bounds = {
        x: Math.floor(this.x + width / 2 - R * LR_FILL_PART * Math.sin(DOWN_ANGLE)),
        y: Math.floor(Math.ceil(this.y + height / 2 + R * UP_DOWN_RATIO)),
        width: Math.floor(2 * R * LR_FILL_PART * Math.sin(DOWN_ANGLE)),
        height: Math.floor(Math.ceil(R * UD_FILL_PART - R * UP_DOWN_RATIO)),
      };
      this.display.defs.addChild(new LinearGradient({
        id: `${this.id}-bg-glossy2`,
        x1: '0%', y1: '0%',
        x2: '0%', y2: '100%',
      }).addChild(
        new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': 0 }),
        new Stop({ offset: '100%', 'stop-color': Color.WHITE, 'stop-opacity': 40 / 255 }),
      ));
      g.addChild(new Ellipse({
        cx: glossy2Bounds.x + (glossy2Bounds.width / 2),
        cy: glossy2Bounds.y + (glossy2Bounds.height / 2),
        rx: glossy2Bounds.width / 2,
        ry: glossy2Bounds.height / 2,
        fill: `url(#${this.id}-bg-glossy2)`,
      }));
    }
  }

  private drawNeedleCenter(g: G, width: number, height: number) {
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-needle-grad`,
      x1: '0%', y1: '0%',
      x2: '100%', y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE }),
      new Stop({ offset: '100%', 'stop-color': BORDER_COLOR }),
    ));
    g.addChild(new Ellipse({
      cx: this.x + (width / 2),
      cy: this.y + (height / 2),
      rx: NEEDLE_DIAMETER / 2,
      ry: NEEDLE_DIAMETER / 2,
      fill: this.effect3d ? `url(#${this.id}-needle-grad)` : Color.GRAY,
    }));
  }
}
