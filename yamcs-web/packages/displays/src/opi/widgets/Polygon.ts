import { ClipPath, G, Polyline as PolylineTag, Rect } from '../../tags';
import { Color } from '../Color';
import { OpiDisplay } from '../OpiDisplay';
import { Point } from '../Point';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class Polygon extends AbstractWidget {

  private alpha: number;
  private lineWidth: number;
  private fillLevel: number;
  private horizontalFill: boolean;
  private lineColor: Color;
  private points: Point[] = [];

  constructor(node: Element, display: OpiDisplay) {
    super(node, display);
    this.alpha = utils.parseIntChild(node, 'alpha');
    this.lineWidth = utils.parseIntChild(node, 'line_width');
    this.fillLevel = utils.parseFloatChild(node, 'fill_level');
    this.horizontalFill = utils.parseBooleanChild(node, 'horizontal_fill');
    const lineColorNode = utils.findChild(node, 'line_color');
    this.lineColor = utils.parseColorChild(lineColorNode);

    const pointsNode = utils.findChild(node, 'points');
    for (const pointNode of utils.findChildren(pointsNode, 'point')) {
      this.points.push({
        x: utils.parseIntAttribute(pointNode, 'x'),
        y: utils.parseIntAttribute(pointNode, 'y'),
      });
    }
  }

  draw(g: G) {
    let opacity = this.alpha / 255;
    if (this.transparent) {
      opacity = 0;
    }

    let points = `${this.points[0].x},${this.points[0].y}`;
    for (let i = 1; i < this.points.length; i++) {
      points += ` ${this.points[i].x},${this.points[i].y}`;
    }

    // Background
    g.addChild(new PolylineTag({
      points,
      fill: this.backgroundColor,
      opacity,
    }));

    // Foreground
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
    g.addChild(new PolylineTag({
      points,
      fill: this.foregroundColor,
      opacity,
      'clip-path': `url(#${this.id}-clip)`,
    }));
  }
}
