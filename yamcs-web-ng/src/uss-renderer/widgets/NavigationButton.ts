import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { G, Rect } from '../tags';

export class NavigationButton extends AbstractWidget {

  parseAndDraw() {
    const pressCmd = utils.findChild(this.node, 'PressCommand');
    const cmdClass = utils.parseStringAttribute(pressCmd, 'class');
    let cmd;
    if (cmdClass === 'OpenDisplayCommand') {
      const displayBaseName = utils.parseStringChild(pressCmd, 'DisplayBasename');
      cmd = `USS.openDisplay('${displayBaseName}')`;
    } else if (cmdClass === 'CloseDisplayCommand') {
      cmd = 'USS.closeDisplay()';
    } else {
      throw new Error(`Unsupported command class ${cmdClass}`);
    }

    const fillStyleNode = utils.findChild(this.node, 'FillStyle');
    const fillColor = utils.parseColorChild(fillStyleNode, 'Color');

    const strokeWidth = 3;
    const boxWidth = this.width - strokeWidth;
    const boxHeight = this.height - strokeWidth;

    const g = new G({
      id: `${this.id}-group`,
      cursor: 'pointer',
      /// onmouseup: cmd,
      class: 'navigation-button',
      'data-name': this.name,
    }).addChild(
      new Rect({
        ...utils.parseFillStyle(this.node),
        stroke: fillColor.brighter().brighter(),
        'stroke-width': strokeWidth,
        'stroke-opacity': 1,
        'shape-rendering': 'crispEdges',
      }).withBorderBox(this.x, this.y, this.width, this.height),
      // Cheap shade effect
      new Rect({
        fill: 'transparent',
        'shape-rendering': 'crispEdges',
        stroke: fillColor.darker(),
        'stroke-width': strokeWidth,
        'stroke-opacity': 1,
        'stroke-dasharray': `0,${boxWidth},${boxWidth + boxHeight},${boxHeight}`,
      }).withBorderBox(this.x, this.y, this.width, this.height),
    );

    const releasedCompoundNode = utils.findChild(this.node, 'ReleasedCompound');
    const elementsNode = utils.findChild(releasedCompoundNode, 'Elements');
    const labelNode = utils.findChild(elementsNode, 'Label');

    const labelWidget = this.display.parseAndDrawWidget(labelNode);
    if (labelWidget) {
      this.display.addWidget(labelWidget, g);
    }

    return g;
  }
}
