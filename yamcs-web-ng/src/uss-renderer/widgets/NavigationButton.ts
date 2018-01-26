import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { G, Rect } from '../tags';
import { Color } from '../Color';
import { Label } from './Label';

export class NavigationButton extends AbstractWidget {

  brightStroke: Color;
  darkStroke: Color;

  bgEl: Element;
  shadeEl: Element;

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
    this.bgEl = buttonEl.children[0];
    this.shadeEl = buttonEl.children[1];
    buttonEl.addEventListener('mousedown', () => {
      this.bgEl.setAttribute('stroke', this.darkStroke.toString());
      this.shadeEl.setAttribute('stroke', this.brightStroke.toString());
    });
    buttonEl.addEventListener('mouseup', () => {
      this.bgEl.setAttribute('stroke', this.brightStroke.toString());
      this.shadeEl.setAttribute('stroke', this.darkStroke.toString());
    });
    buttonEl.addEventListener('mouseout', () => {
      this.bgEl.setAttribute('stroke', this.brightStroke.toString());
      this.shadeEl.setAttribute('stroke', this.darkStroke.toString());
    });
  }

  updateProperty(property: string, value: any, acquisitionStatus: string, monitoringResult: string) {
    switch (property) {
      case 'FILL_COLOR':
        const newColor = Color.forName(value);
        this.brightStroke = newColor.brighter().brighter();
        this.darkStroke = newColor.darker();

        this.bgEl.setAttribute('fill', newColor.toString());
        this.bgEl.setAttribute('stroke', this.brightStroke.toString());
        this.shadeEl.setAttribute('stroke', this.darkStroke.toString());
        break;
      default:
        console.warn('Unsupported dynamic property: ' + property);
    }
  }
}
