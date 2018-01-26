import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { G, Rect } from '../tags';
import { Color } from '../Color';
import { Label } from './Label';

export class NavigationButton extends AbstractWidget {

  brightStroke: Color;
  darkStroke: Color;

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

    this.brightStroke = fillColor.brighter().brighter();
    this.darkStroke = fillColor.darker();

    const g = new G({
      id: this.id,
      cursor: 'pointer',
      /// onmouseup: cmd,
      class: 'navigation-button',
      'data-name': this.name,
    }).addChild(
      new Rect({
        ...utils.parseFillStyle(this.node),
        stroke: this.brightStroke,
        'stroke-width': strokeWidth,
        'stroke-opacity': 1,
        'shape-rendering': 'crispEdges',
      }).withBorderBox(this.x, this.y, this.width, this.height),
      // Cheap shade effect
      new Rect({
        fill: 'transparent',
        'shape-rendering': 'crispEdges',
        stroke: this.darkStroke,
        'stroke-width': strokeWidth,
        'stroke-opacity': 1,
        'stroke-dasharray': `0,${boxWidth},${boxWidth + boxHeight},${boxHeight}`,
      }).withBorderBox(this.x, this.y, this.width, this.height),
    );

    const releasedCompoundNode = utils.findChild(this.node, 'ReleasedCompound');
    const elementsNode = utils.findChild(releasedCompoundNode, 'Elements');
    const labelNode = utils.findChild(elementsNode, 'Label');

    const labelWidget = new Label(labelNode, this.display, false);
    labelWidget.tag = labelWidget.parseAndDraw();
    this.display.addWidget(labelWidget, g);

    return g;
  }

  afterDomAttachment() {
    const buttonEl = this.svg.getElementById(this.id);
    const topStroke = buttonEl.children[0];
    const bottomStroke = buttonEl.children[1];
    buttonEl.addEventListener('mousedown', () => {
      topStroke.setAttribute('stroke', this.darkStroke.toString());
      bottomStroke.setAttribute('stroke', this.brightStroke.toString());
    });
    buttonEl.addEventListener('mouseup', () => {
      topStroke.setAttribute('stroke', this.brightStroke.toString());
      bottomStroke.setAttribute('stroke', this.darkStroke.toString());
    });
    buttonEl.addEventListener('mouseout', () => {
      topStroke.setAttribute('stroke', this.brightStroke.toString());
      bottomStroke.setAttribute('stroke', this.darkStroke.toString());
    });
  }

  updateProperty(property: string, value: any, acquisitionStatus: string, monitoringResult: string) {
    console.warn('Unsupported dynamic property: ' + property);
  }
}
