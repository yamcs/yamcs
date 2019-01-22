import { G, Polyline as PolylineTag } from '../../tags';
import { OpiDisplay } from '../OpiDisplay';
import { Point } from '../Point';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class Polyline extends AbstractWidget {

  private alpha: number;
  private lineWidth: number;
  private points: Point[] = [];

  constructor(node: Element, display: OpiDisplay) {
    super(node, display);
    this.alpha = utils.parseIntChild(node, 'alpha');
    this.lineWidth = utils.parseIntChild(node, 'line_width');

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

    g.addChild(new PolylineTag({
      points,
      fill: 'none',
      stroke: this.backgroundColor,
      'stroke-width': this.lineWidth,
      opacity,
    }));
  }
}
