import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';

export class Polyline extends AbstractWidget {

  parseAndDraw(svg: any, parent: any, e: Node) {
    const points: any[] = [];
    for (const child of utils.findChildren(e, 'Point')) {
      points.push([
        utils.parseStringChild(child, 'x'),
        utils.parseStringChild(child, 'y'),
      ]);
    }

    const settings: {[key: string]: any} = {
      fill: 'none',
      ...utils.parseDrawStyle(e),
    };

    if (utils.parseBooleanChild(e, 'ArrowStart')) {
      settings.markerStart = 'url(#uss-arrowStart)';
    }
    if (utils.parseBooleanChild(e, 'ArrowEnd')) {
      settings.markerEnd = 'url(#uss-arrowEnd)';
    }

    svg.polyline(parent, points, settings);
  }
}
