import { G, Line, Polyline } from '../../tags';
import { Color } from '../../uss/Color';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from '../widgets/AbstractWidget';

interface Point {
  x: number;
  y: number;
}

export class Connection {

  private name: string;
  private lineColor: Color;
  private lineWidth: number;
  private router: number;

  private sourceWidget: AbstractWidget;
  private sourceTerm: string;

  private targetWidget: AbstractWidget;
  private targetTerm: string;

  private points: Point[] = [];

  constructor(protected node: Element, protected display: OpiDisplay) {
    this.name = utils.parseStringChild(node, 'name');

    const lineColorNode = utils.findChild(node, 'line_color');
    this.lineColor = utils.parseColorChild(lineColorNode);
    this.lineWidth = utils.parseIntChild(node, 'line_width');

    const srcWuid = utils.parseStringChild(node, 'src_wuid');
    this.sourceWidget = display.findWidget(srcWuid)!;
    this.sourceTerm = utils.parseStringChild(node, 'src_term');

    const tgtWuid = utils.parseStringChild(node, 'tgt_wuid');
    this.targetWidget = display.findWidget(tgtWuid)!;
    this.targetTerm = utils.parseStringChild(node, 'tgt_term');

    this.router = utils.parseIntChild(node, 'router');

    const pointsNode = utils.findChild(node, 'points');
    for (const pointNode of utils.findChildren(pointsNode, 'point')) {
      this.points.push({
        x: utils.parseIntAttribute(pointNode, 'x'),
        y: utils.parseIntAttribute(pointNode, 'y'),
      });
    }
  }

  draw() {
    const g = new G({
      class: 'connection',
      'data-name': this.name,
    });

    if (this.points.length) { // Only present if the user has manually repositioned a mid-point.
      this.drawPath(g);
    } else if (this.router === 0) {
      this.drawManhattanConnection(g);
    } else if (this.router === 1) {
      this.drawDirectConnection(g);
    }

    return g;
  }

  drawPath(g: G) {
    const from = this.getPosition(this.sourceWidget, this.sourceTerm);
    const to = this.getPosition(this.targetWidget, this.targetTerm);

    // Add 0.5 to avoid fuzzy lines
    let points = `${from.x + 0.5},${from.y + 0.5}`;
    for (const point of this.points) {
      points += ` ${point.x + 0.5},${point.y + 0.5}`;
    }
    points += ` ${to.x + 0.5},${to.y + 0.5}`;

    g.addChild(new Polyline({
      points,
      fill: 'none',
      stroke: this.lineColor,
      'stroke-width': this.lineWidth,
    }));
  }

  drawManhattanConnection(g: G) {
    // TODO
    this.drawDirectConnection(g);
  }

  drawDirectConnection(g: G) {
    const from = this.getPosition(this.sourceWidget, this.sourceTerm);
    const x1 = from.x;
    const y1 = from.y;

    const to = this.getPosition(this.targetWidget, this.targetTerm);
    const x2 = to.x;
    const y2 = to.y;

    g.addChild(new Line({
      x1, x2, y1, y2,
      stroke: this.lineColor,
      'stroke-width': this.lineWidth,
    }));
  }

  private getPosition(widget: AbstractWidget, term: string): Point {
    switch (term) {
      case 'LEFT':
        return {
          x: widget.holderX,
          y: widget.holderY + (widget.holderHeight / 2),
        };
      case 'TOP':
        return {
          x: widget.holderX + (widget.holderWidth / 2),
          y: widget.holderY,
        };
      case 'RIGHT':
        return {
          x: widget.holderX + widget.holderWidth,
          y: widget.holderY + (widget.holderHeight / 2),
        };
      case 'BOTTOM':
        return {
          x: widget.holderX + (widget.holderWidth / 2),
          y: widget.holderY + widget.holderHeight,
        };
      case 'TOP_LEFT':
        return {
          x: widget.holderX,
          y: widget.holderY,
        };
      case 'TOP_RIGHT':
        return {
          x: widget.holderX + widget.holderWidth,
          y: widget.holderY,
        };
      case 'BOTTOM_LEFT':
        return {
          x: widget.holderX,
          y: widget.holderY + widget.holderHeight,
        };
      case 'BOTTOM_RIGHT':
        return {
          x: widget.holderX + widget.holderWidth,
          y: widget.holderY + widget.holderHeight,
        };
      default:
        throw Error(`Unexpected term ${term}`);
    }
  }
}
