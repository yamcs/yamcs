import { ClipPath, Ellipse as EllipseTag, G, LinearGradient, Rect, Stop } from '../../tags';
import { Color } from '../Color';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class Ellipse extends AbstractWidget {

  private alpha: number;
  private lineWidth: number;
  private fillLevel: number;
  private horizontalFill: boolean;
  private lineColor: Color;
  private foregroundGradientStartColor: Color;
  private backgroundGradientStartColor: Color;
  private gradient: boolean;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    this.alpha = utils.parseIntChild(node, 'alpha');
    this.lineWidth = utils.parseIntChild(node, 'line_width');
    this.fillLevel = utils.parseFloatChild(node, 'fill_level');
    this.horizontalFill = utils.parseBooleanChild(node, 'horizontal_fill');
    const lineColorNode = utils.findChild(node, 'line_color');
    this.lineColor = utils.parseColorChild(lineColorNode);
    const backgroundGradientStartColorNode = utils.findChild(node, 'bg_gradient_color');
    this.backgroundGradientStartColor = utils.parseColorChild(backgroundGradientStartColorNode);
    const foregroundGradientStartColorNode = utils.findChild(node, 'fg_gradient_color');
    this.foregroundGradientStartColor = utils.parseColorChild(foregroundGradientStartColorNode);
    this.gradient = utils.parseBooleanChild(node, 'gradient');
  }

  draw(g: G) {
    let opacity = this.alpha / 255;
    if (this.transparent) {
      opacity = 0;
    }

    // Background
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-gradient-bg`,
      x1: '0%',
      y1: '0%',
      x2: this.horizontalFill ? '0%' : '100%',
      y2: this.horizontalFill ? '100%' : '0%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': this.backgroundGradientStartColor }),
      new Stop({ offset: '100%', 'stop-color': this.backgroundColor }),
    ));
    g.addChild(new EllipseTag({
      cx: this.x + (this.width / 2),
      cy: this.y + (this.height / 2),
      rx: this.width / 2,
      ry: this.height / 2,
      fill: this.gradient ? `url(#${this.id}-gradient-bg)` : this.backgroundColor,
      stroke: this.lineColor,
      'stroke-width': this.lineWidth,
      opacity,
    }));

    // Foreground
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-gradient-fg`,
      x1: '0%',
      y1: '0%',
      x2: this.horizontalFill ? '0%' : '100%',
      y2: this.horizontalFill ? '100%' : '0%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': this.foregroundGradientStartColor }),
      new Stop({ offset: '100%', 'stop-color': this.foregroundColor }),
    ));

    let fillY = this.y;
    let fillWidth = this.width;
    let fillHeight = this.height;
    if (this.horizontalFill) {
      fillWidth *= (this.fillLevel / 100);
    } else {
      fillHeight *= (this.fillLevel / 100);
      fillY += fillHeight;
    }

    g.addChild(new ClipPath({
      id: `${this.id}-clip`,
    }).addChild(new Rect({
      x: this.x - (this.lineWidth / 2.0),
      y: fillY - (this.lineWidth / 2.0),
      width: fillWidth + this.lineWidth,
      height: fillHeight + this.lineWidth,
    })));
    g.addChild(new EllipseTag({
      cx: this.x + (this.width / 2),
      cy: this.y + (this.height / 2),
      rx: this.width / 2,
      ry: this.height / 2,
      fill: this.gradient ? `url(#${this.id}-gradient-fg)` : this.foregroundColor,
      opacity,
      'clip-path': `url(#${this.id}-clip)`,
    }));
  }
}
