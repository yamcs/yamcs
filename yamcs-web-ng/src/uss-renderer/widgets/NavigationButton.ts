import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';

export class NavigationButton extends AbstractWidget {

  parseAndDraw(svg: any, parent: any, e: Node) {
    const pressCmd = utils.findChild(e, 'PressCommand');
    const cmdClass = utils.parseStringAttribute(pressCmd, 'class');
    let cmd;
    if (cmdClass === 'OpenDisplayCommand') {
      const displayBaseName = utils.parseStringChild(pressCmd, 'DisplayBasename');
      cmd = `USS.openDisplay('${displayBaseName}')`;
    } else if (cmdClass === 'CloseDisplayCommand') {
      cmd = 'USS.closeDisplay()';
    } else {
      console.warn('Unsupported command class ' + cmdClass);
      return;
    }


    const settings = {
      ...utils.parseFillStyle(e),
      strokeOpacity: 1,
      stroke: 'rgba(0,0,0,255)',
      strokeWidth: '1.0',
    };

    if ('strokeWidth' in settings) {
        this.x += 0.5;
        this.y += 0.5;
    }
    parent = svg.group(parent, this.id + '-group', {cursor: 'pointer', onmouseup: cmd});
    svg.rect(parent, this.x, this.y, this.width, this.height, settings);

    const releasedCompoundNode = utils.findChild(e, 'ReleasedCompound');
    const elementsNode = utils.findChild(releasedCompoundNode, 'Elements');
    const labelNode = utils.findChild(elementsNode, 'Label');

    const textStyle = utils.findChild(labelNode, 'TextStyle');
    const text = utils.parseStringChild(labelNode, 'Text');
    utils.writeText(svg, parent, this, textStyle, text);
  }
}
