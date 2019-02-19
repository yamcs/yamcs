import { Ellipse, G, LinearGradient, Polyline, Rect, Stop } from '../../tags';
import { Color } from '../Color';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

interface State {
  label: string;
  color: Color;
  value?: number;
}

export class LED extends AbstractWidget {

  private squareLed: boolean;
  private effect3d: boolean;

  private states: State[] = [];
  private fallback?: State;

  private bulbColor: Color;
  private bulbBorderColor: Color;
  private bulbBorder: number;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    const stateCount = utils.parseIntChild(this.node, 'state_count', 2);
    if (stateCount === 2) {
      const offColorNode = utils.findChild(this.node, 'off_color');
      this.states.push({
        label: utils.parseStringChild(this.node, 'off_label'),
        color: utils.parseColorChild(offColorNode),
      });
      const onColorNode = utils.findChild(this.node, 'on_color');
      this.states.push({
        label: utils.parseStringChild(this.node, 'on_label'),
        color: utils.parseColorChild(onColorNode),
      });
    } else {
      for (let i = 0; i < stateCount; i++) {
        const colorNode = utils.findChild(this.node, `state_color_${i}`);
        this.states.push({
          label: utils.parseStringChild(this.node, `state_label_${i}`),
          color: utils.parseColorChild(colorNode),
          value: utils.parseFloatChild(this.node, `state_value_${i}`),
        });
        const fallbackColorNode = utils.findChild(this.node, 'state_color_fallback');
        this.fallback = {
          label: utils.parseStringChild(this.node, 'state_label_fallback'),
          color: utils.parseColorChild(fallbackColorNode),
        };
      }
    }

    // Initial state
    this.bulbColor = this.fallback ? this.fallback.color : this.states[0].color;
    for (const state of this.states) {
      if (state.value === 0) {
        this.bulbColor = state.color;
      }
    }

    // Old displays don't have these properties
    this.bulbBorder = utils.parseIntChild(this.node, 'bulb_border', 3);
    this.bulbBorderColor = Color.DARK_GRAY;
    if (utils.hasChild(this.node, 'bulb_border_color')) {
      const bulbBorderColorNode = utils.findChild(this.node, 'bulb_border_color');
      this.bulbBorderColor = utils.parseColorChild(bulbBorderColorNode);
    }

    this.squareLed = utils.parseBooleanChild(this.node, 'square_led');
    this.effect3d = utils.parseBooleanChild(this.node, 'effect_3d');
  }

  draw(g: G) {
    if (this.squareLed) {
      if (this.effect3d) {
        this.drawSquare3d(g);
      } else {
        this.drawSquare2d(g);
      }
    } else {
      if (this.effect3d) {
        this.drawCircle3d(g);
      } else {
        this.drawCirlce2d(g);
      }
    }
  }

  private drawCirlce2d(g: G) {
    const ellipseBg = new Ellipse({
      fill: this.bulbColor,
      stroke: this.bulbBorderColor.toString(),
      'stroke-width': this.bulbBorder,
    }).withBorderBox(
      this.x + (this.width / 2),
      this.y + (this.height / 2),
      this.width / 2,
      this.height / 2,
    );
    g.addChild(ellipseBg);
  }

  private drawCircle3d(g: G) {
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-r-b`,
      x1: '0%', y1: '0%',
      x2: '100%', y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': this.bulbBorderColor, 'stop-opacity': '1' }),
      new Stop({ offset: '100%', 'stop-color': this.bulbBorderColor, 'stop-opacity': '0', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-r`,
      x1: '0%', y1: '0%',
      x2: '100%', y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '1' }),
      new Stop({ offset: '100%', 'stop-color': this.bulbBorderColor, 'stop-opacity': '0', }),
    ));

    g.addChild(new Ellipse({
      cx: this.x + (this.width / 2),
      cy: this.y + (this.height / 2),
      rx: this.width / 2,
      ry: this.height / 2,
      fill: Color.WHITE,
    }));
    g.addChild(new Ellipse({
      cx: this.x + (this.width / 2),
      cy: this.y + (this.height / 2),
      rx: this.width / 2,
      ry: this.height / 2,
      fill: `url(#${this.id}-r-b)`,
    }));

    const innerWidth = this.width - (2 * this.bulbBorder);
    const innerHeight = this.height - (2 * this.bulbBorder);
    g.addChild(new Ellipse({
      cx: this.x + (this.width / 2),
      cy: this.y + (this.height / 2),
      rx: innerWidth / 2,
      ry: innerHeight / 2,
      fill: this.bulbColor,
    }));
    g.addChild(new Ellipse({
      cx: this.x + (this.width / 2),
      cy: this.y + (this.height / 2),
      rx: innerWidth / 2,
      ry: innerHeight / 2,
      fill: `url(#${this.id}-r)`,
    }));
  }

  private drawSquare2d(g: G) {
    const squareBg = new Rect({
      fill: this.bulbColor,
      stroke: this.bulbBorderColor.toString(),
      'stroke-width': this.bulbBorder,
    }).withBorderBox(this.x, this.y, this.width, this.height);
    g.addChild(squareBg);
  }

  private drawSquare3d(g: G) {
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq-l`,
      x1: '0%', y1: '0%',
      x2: '100%', y2: '0%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': '0.078' }),
      new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': '0.39', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq-t`,
      x1: '0%', y1: '0%',
      x2: '0%', y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': '0.078' }),
      new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': '0.39', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq-r`,
      x1: '0%', y1: '0%',
      x2: '100%', y2: '0%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '0.078' }),
      new Stop({ offset: '100%', 'stop-color': Color.WHITE, 'stop-opacity': '0.39', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq-b`,
      x1: '0%', y1: '0%',
      x2: '0%', y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '0.078' }),
      new Stop({ offset: '100%', 'stop-color': Color.WHITE, 'stop-opacity': '0.39', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq`,
      x1: '0%', y1: '0%',
      x2: '100%', y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '0.784' }),
      new Stop({ offset: '100%', 'stop-color': this.bulbColor, 'stop-opacity': '0', }),
    ));

    const borderBg = new Rect({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      fill: this.bulbBorderColor.toString(),
    });
    g.addChild(borderBg);

    let points = `${this.x},${this.y}`;
    points += ` ${this.x + this.bulbBorder},${this.y + this.bulbBorder}`;
    points += ` ${this.x + this.bulbBorder},${this.y + this.height - this.bulbBorder}`;
    points += ` ${this.x},${this.y + this.height}`;
    g.addChild(new Polyline({ points, fill: `url(#${this.id}-sq-l)` }));

    points = `${this.x},${this.y}`;
    points += ` ${this.x + this.bulbBorder},${this.y + this.bulbBorder}`;
    points += ` ${this.x + this.width - this.bulbBorder},${this.y + this.bulbBorder}`;
    points += ` ${this.x + this.width},${this.y}`;
    g.addChild(new Polyline({ points, fill: `url(#${this.id}-sq-t)` }));

    points = `${this.x + this.width},${this.y}`;
    points += ` ${this.x + this.width - this.bulbBorder},${this.y + this.bulbBorder}`;
    points += ` ${this.x + this.width - this.bulbBorder},${this.y + this.height - this.bulbBorder}`;
    points += ` ${this.x + this.width},${this.y + this.height}`;
    g.addChild(new Polyline({ points, fill: `url(#${this.id}-sq-r)` }));

    points = `${this.x},${this.y + this.height}`;
    points += ` ${this.x + this.bulbBorder},${this.y + this.height - this.bulbBorder}`;
    points += ` ${this.x + this.width - this.bulbBorder},${this.y + this.height - this.bulbBorder}`;
    points += ` ${this.x + this.width},${this.y + this.height}`;
    g.addChild(new Polyline({ points, fill: `url(#${this.id}-sq-b)` }));

    // Bulb
    g.addChild(new Rect({
      x: this.x + this.bulbBorder,
      y: this.y + this.bulbBorder,
      width: this.width - (2 * this.bulbBorder),
      height: this.height - (2 * this.bulbBorder),
      fill: this.bulbColor,
    }));

    // Bulb gradient overlay
    g.addChild(new Rect({
      x: this.x + this.bulbBorder,
      y: this.y + this.bulbBorder,
      width: this.width - (2 * this.bulbBorder),
      height: this.height - (2 * this.bulbBorder),
      fill: `url(#${this.id}-sq)`,
    }));
  }
}
