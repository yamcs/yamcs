import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Tag } from '../tags';
import { DataSourceSample } from '../DataSourceSample';
import { DataSourceBinding } from '../DataSourceBinding';

export class Polyline extends AbstractWidget {

  parseAndDraw() {
    const drawStyle = utils.parseDrawStyle(this.node);
    const strokeWidth = drawStyle['stroke-width'];

    const points: string[] = [];
    const pointsEl = utils.findChild(this.node, 'Points');
    for (const child of utils.findChildren(pointsEl)) {
      const x = utils.parseFloatChild(child, 'x');
      const y = utils.parseFloatChild(child, 'y');
      points.push(`${x + (strokeWidth / 2.0)},${y + (strokeWidth / 2.0)}`);
    }

    const line = new Tag('polyline', {
      fill: 'none',
      points: points.join(' '),
      ...drawStyle,
      class: 'polyline',
      'data-name': this.name,
      'shape-rendering': 'crispEdges',
    });

    if (utils.parseBooleanChild(this.node, 'ArrowStart')) {
      line.setAttribute('marker-start', 'url(#uss-arrowStart)');
    }
    if (utils.parseBooleanChild(this.node, 'ArrowEnd')) {
      line.setAttribute('marker-end', 'url(#uss-arrowEnd)');
    }

    return line;
  }

  updateBinding(binding: DataSourceBinding, sample: DataSourceSample) {
    console.warn('Unsupported binding update: ', binding);
  }
}
