import { G, Path } from '../../tags';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

// Not fully functional yet..
export class Arc extends AbstractWidget {

  private alpha: number;
  private startAngle: number;
  private totalAngle: number;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    this.alpha = utils.parseIntChild(node, 'alpha');
    this.startAngle = utils.parseIntChild(node, 'start_angle');
    this.totalAngle = utils.parseIntChild(node, 'total_angle');
  }

  draw(g: G) {
    let opacity = this.alpha / 255;
    if (this.transparent) {
      opacity = 0;
    }

    const cx = this.x + (this.width / 2);
    const cy = this.y + (this.height / 2);
    const rx = this.width / 2;
    const ry = this.height / 2;

    const startAngle = this.startAngle;
    const endAngle = this.startAngle - this.totalAngle;
    g.addChild(new Path({
      d: this.describeArc(cx, cy, rx, ry, startAngle, endAngle),
      fill: 'none',
      stroke: this.foregroundColor,
      opacity,
    }));
  }

  private describeArc(cx: number, cy: number, rx: number, ry: number, startAngle: number, endAngle: number) {
    const start = this.polarToCartesian(cx, cy, rx, ry, startAngle);
    const end = this.polarToCartesian(cx, cy, rx, ry, endAngle);

    const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1';
    const d = [
        'M', start.x, start.y,
        'A', rx, ry, 0, largeArcFlag, 0, end.x, end.y
    ].join(' ');

    return d;
  }

  private polarToCartesian(cx: number, cy: number, rx: number, ry: number, angle: number) {
    const rad = (angle - 0) * Math.PI / 180.0;
    return {
      x: cx + (rx * Math.cos(rad)),
      y: cy + (ry * Math.sin(rad)),
    };
  }
}
