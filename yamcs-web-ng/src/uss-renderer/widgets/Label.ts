import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';

export class Label extends AbstractWidget {

  parseAndDraw(svg: any, parent: any, node: Node) {
    const text = utils.parseStringChild(node, 'Text');
    const textStyle = utils.findChild(node, 'TextStyle');
    utils.writeText(svg, parent, this, textStyle, text);
  }
}
