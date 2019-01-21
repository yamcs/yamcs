import { Ellipse, G, LinearGradient, Polyline, Rect, Stop, Tag } from '../../tags';
import { Color } from '../Color';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

interface State {
  label: string;
  color: Color;
  value?: number;
}

export class LED extends AbstractWidget {

  private states: State[] = [];
  private fallback?: State;

  private holderBorder: number;

  private bulbColor: Color;
  private bulbBorderColor: Color;
  private bulbBorder: number;
  private bulbWidth: number;
  private bulbHeight: number;

  parseAndDraw() {
    const g = new G({
      class: 'led',
      'data-name': this.name,
    });

    const holder = new Rect({
      ...this.borderStyle,
      'pointer-events': 'none',
      'fill-opacity': '0',
    }).withBorderBox(this.x, this.y, this.width, this.height);

    // This is a weird one. When there is no widget border the LED
    // shrinks according to an inset of 2px. This only happens when
    // the border is alarm-sensitive.
    if (!holder.attributes['stroke'] && this.borderAlarmSensitive) {
      holder.setAttribute('stroke-width', '2');
    }

    g.addChild(holder);

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

    this.holderBorder = Number(holder.attributes['stroke-width'] || 0);
    this.bulbWidth = this.width - (2 * this.holderBorder);
    this.bulbHeight = this.height - (2 * this.holderBorder);

    const squareLed = utils.parseBooleanChild(this.node, 'square_led');

    if (this.effect3d) {
      this.display.defs.addChild(new Tag('linearGradient', {
        id: `${this.id}-grad`,
        x1: '0%',
        y1: '0%',
        x2: squareLed ? '0%' : '100%',
        y2: '100%',
      }).addChild(
        new Tag('stop', {
          offset: '-30%',
          'stop-color': this.bulbColor,
          'stop-opacity': '0'
        }),
        new Tag('stop', {
          offset: '80%',
          'stop-color': this.bulbColor,
          'stop-opacity': '1',
        })
      ));
    }

    if (squareLed) {
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

    return g;
  }

  private drawCirlce2d(g: G) {
    const ellipseBg = new Ellipse({
      fill: this.bulbColor,
      stroke: this.bulbBorderColor.toString(),
      'stroke-width': this.bulbBorder,
    }).withBorderBox(
      this.x + this.holderBorder + (this.bulbWidth / 2),
      this.y + this.holderBorder + (this.bulbHeight / 2),
      this.bulbWidth / 2,
      this.bulbHeight / 2,
    );
    g.addChild(ellipseBg);
  }

  private drawCircle3d(g: G) {
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-r-b`,
      x1: '0%',
      y1: '0%',
      x2: '100%',
      y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': this.bulbBorderColor, 'stop-opacity': '1' }),
      new Stop({ offset: '100%', 'stop-color': this.bulbBorderColor, 'stop-opacity': '0', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-r`,
      x1: '0%',
      y1: '0%',
      x2: '100%',
      y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '1' }),
      new Stop({ offset: '100%', 'stop-color': this.bulbBorderColor, 'stop-opacity': '0', }),
    ));

    const x = this.x + this.holderBorder;
    const y = this.y + this.holderBorder;

    g.addChild(new Ellipse({
      cx: x + (this.bulbWidth / 2),
      cy: y + (this.bulbHeight / 2),
      rx: this.bulbWidth / 2,
      ry: this.bulbHeight / 2,
      fill: Color.WHITE,
    }));
    g.addChild(new Ellipse({
      cx: x + (this.bulbWidth / 2),
      cy: y + (this.bulbHeight / 2),
      rx: this.bulbWidth / 2,
      ry: this.bulbHeight / 2,
      fill: `url(#${this.id}-r-b)`,
    }));

    const innerWidth = this.bulbWidth - (2 * this.bulbBorder);
    const innerHeight = this.bulbHeight - (2 * this.bulbBorder);
    g.addChild(new Ellipse({
      cx: x + (this.bulbWidth / 2),
      cy: y + (this.bulbHeight / 2),
      rx: innerWidth / 2,
      ry: innerHeight / 2,
      fill: this.bulbColor,
    }));
    g.addChild(new Ellipse({
      cx: x + (this.bulbWidth / 2),
      cy: y + (this.bulbHeight / 2),
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
    }).withBorderBox(
      this.x + this.holderBorder, this.y + this.holderBorder, this.bulbWidth, this.bulbHeight
    );
    g.addChild(squareBg);
  }

  private drawSquare3d(g: G) {
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq-l`,
      x1: '0%',
      y1: '0%',
      x2: '100%',
      y2: '0%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': '0.078' }),
      new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': '0.39', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq-t`,
      x1: '0%',
      y1: '0%',
      x2: '0%',
      y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': '0.078' }),
      new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': '0.39', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq-r`,
      x1: '0%',
      y1: '0%',
      x2: '100%',
      y2: '0%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '0.078' }),
      new Stop({ offset: '100%', 'stop-color': Color.WHITE, 'stop-opacity': '0.39', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq-b`,
      x1: '0%',
      y1: '0%',
      x2: '0%',
      y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '0.078' }),
      new Stop({ offset: '100%', 'stop-color': Color.WHITE, 'stop-opacity': '0.39', }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-sq`,
      x1: '0%',
      y1: '0%',
      x2: '100%',
      y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '0.784' }),
      new Stop({ offset: '100%', 'stop-color': this.bulbColor, 'stop-opacity': '0', }),
    ));

    const x = this.x + this.holderBorder;
    const y = this.y + this.holderBorder;

    const borderBg = new Rect({
      x,
      y,
      width: this.bulbWidth,
      height: this.bulbHeight,
      fill: this.bulbBorderColor.toString(),
    });
    g.addChild(borderBg);

    let points = `${x},${y}`;
    points += ` ${x + this.bulbBorder},${y + this.bulbBorder}`;
    points += ` ${x + this.bulbBorder},${y + this.bulbHeight - this.bulbBorder}`;
    points += ` ${x},${y + this.bulbHeight}`;
    g.addChild(new Polyline({ points, fill: `url(#${this.id}-sq-l)` }));

    points = `${x},${y}`;
    points += ` ${x + this.bulbBorder},${y + this.bulbBorder}`;
    points += ` ${x + this.bulbWidth - this.bulbBorder},${y + this.bulbBorder}`;
    points += ` ${x + this.bulbWidth},${y}`;
    g.addChild(new Polyline({ points, fill: `url(#${this.id}-sq-t)` }));

    points = `${x + this.bulbWidth},${y}`;
    points += ` ${x + this.bulbWidth - this.bulbBorder},${y + this.bulbBorder}`;
    points += ` ${x + this.bulbWidth - this.bulbBorder},${y + this.bulbHeight - this.bulbBorder}`;
    points += ` ${x + this.bulbWidth},${y + this.bulbHeight}`;
    g.addChild(new Polyline({ points, fill: `url(#${this.id}-sq-r)` }));

    points = `${x},${y + this.bulbHeight}`;
    points += ` ${x + this.bulbBorder},${y + this.bulbHeight - this.bulbBorder}`;
    points += ` ${x + this.bulbWidth - this.bulbBorder},${y + this.bulbHeight - this.bulbBorder}`;
    points += ` ${x + this.bulbWidth},${y + this.bulbHeight}`;
    g.addChild(new Polyline({ points, fill: `url(#${this.id}-sq-b)` }));

    // Bulb
    g.addChild(new Rect({
      x: x + this.bulbBorder,
      y: y + this.bulbBorder,
      width: this.bulbWidth - (2 * this.bulbBorder),
      height: this.bulbHeight - (2 * this.bulbBorder),
      fill: this.bulbColor,
    }));

    // Bulb gradient overlay
    g.addChild(new Rect({
      x: x + this.bulbBorder,
      y: y + this.bulbBorder,
      width: this.bulbWidth - (2 * this.bulbBorder),
      height: this.bulbHeight - (2 * this.bulbBorder),
      fill: `url(#${this.id}-sq)`,
    }));
  }
}
