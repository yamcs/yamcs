import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Tag, G, Defs, Marker, Path } from '../../tags';
import { DataSourceBinding } from '../DataSourceBinding';

export class Polyline extends AbstractWidget {

  parseAndDraw() {
    const drawStyleNode = utils.findChild(this.node, 'DrawStyle');
    const drawColor = utils.parseColorChild(drawStyleNode, 'Color');
    const strokeWidth = utils.parseFloatChild(drawStyleNode, 'Width');
    const arrowStart = utils.parseBooleanChild(this.node, 'ArrowStart');
    const arrowEnd = utils.parseBooleanChild(this.node, 'ArrowEnd');


    const points: string[] = [];
    const pointsEl = utils.findChild(this.node, 'Points');

    let crispEdges = true;
    let prevX;
    let prevY;
    for (const child of utils.findChildren(pointsEl)) {
      const x = utils.parseFloatChild(child, 'x');
      const y = utils.parseFloatChild(child, 'y');
      points.push(`${x + (strokeWidth / 2)},${y + (strokeWidth / 2)}`);

      // If all the path segments are vertical or horizontal, then apply crispEdges
      if (prevX !== undefined && prevY !== undefined) {
        if (prevX !== x && prevY !== y) {
          crispEdges = false;
        }
      }
      prevX = x;
      prevY = y;
    }

    const g = new G({
      class: 'polyline',
      'data-name': this.name,
    });

    const defs = new Defs();
    if (arrowStart || arrowEnd) {
      g.addChild(defs);
    }

    const line = new Tag('polyline', {
      fill: 'none',
      points: points.join(' '),
      stroke: drawColor,
      'stroke-width': strokeWidth,
    });
    if (crispEdges) {
      line.setAttribute('shape-rendering', 'crispEdges');
    }

    if (arrowStart) {
      defs.addChild(new Marker({
        id: `${this.id}-start`,
        refX: 2,
        refY: 6,
        markerWidth: 20,
        markerHeight: 20,
        orient: 'auto',
        style: `overflow: visible; fill: ${drawColor}; stroke: none`,
      }).addChild(new Path({
        d: 'M2,2 L2,11 L10,6 L2,2',
        transform: 'rotate(180 2 6)',
      })));
      line.setAttribute('marker-start', `url(#${this.id}-start)`);
    }
    if (arrowEnd) {
      defs.addChild(new Marker({
        id: `${this.id}-end`,
        refX: 2,
        refY: 6,
        markerWidth: 20,
        markerHeight: 20,
        orient: 'auto',
        style: `overflow: visible; fill: ${drawColor}; stroke: none`,
      }).addChild(new Path({
        d: 'M2,2 L2,11 L10,6 L2,2',
      })));
      line.setAttribute('marker-end', `url(#${this.id}-end)`);
    }

    g.addChild(line);

    return g;
  }

  registerBinding(binding: DataSourceBinding) {
    console.warn('Unsupported dynamic property: ' + binding.dynamicProperty);
  }
}
