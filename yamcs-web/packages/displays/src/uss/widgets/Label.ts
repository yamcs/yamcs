import { ClipPath, G, Rect, Text } from '../../tags';
import { DataSourceBinding } from '../DataSourceBinding';
import { UssDisplay } from '../UssDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class Label extends AbstractWidget {

  constructor(node: Element, display: UssDisplay) {
    super(node, display);
  }

  parseAndDraw() {
    const g = new G({
      class: 'label',
      'data-name': this.name,
    });
    let innerText = utils.parseStringChild(this.node, 'Text');
    innerText = innerText.split(' ').join('\u00a0'); // Preserve whitespace

    const textStyleNode = utils.findChild(this.node, 'TextStyle');

    // Clip text within the defined boundary.
    // Should be exceptional because USS makes it really hard to make Labels
    // smaller than its content.
    // TODO clip-path (nor -webkit-clip-path) does not work on Safari
    const clipId = this.generateChildId();
    g.addChild(new ClipPath({ id: clipId }).addChild(
      new Rect({
        x: this.x,
        y: this.y,
        width: this.width,
        height: this.height,
      })
    ));

    const text = new Text({
      id: this.id,
      y: this.y,
      ...utils.parseTextStyle(textStyleNode),
      'clip-path': `url(#${clipId})`,
      'pointer-events': 'none',
    }, innerText);
    g.addChild(text);

    let x;
    const horizAlignment = utils.parseStringChild(textStyleNode, 'HorizontalAlignment');
    if (horizAlignment === 'CENTER') {
      x = this.x + this.width / 2;
      text.setAttribute('text-anchor', 'middle');
    } else if (horizAlignment === 'LEFT') {
      x = this.x;
      text.setAttribute('text-anchor', 'start');
    } else if (horizAlignment === 'RIGHT') {
      x = this.x + this.width;
      text.setAttribute('text-anchor', 'end');
    }
    text.setAttribute('x', String(x));

    // Prefer FontMetrics over baseline tricks to account for
    // ascends and descends.
    let fontSize = text.attributes['font-size'];
    const fontFamily = text.attributes['font-family'];
    const fontStyle = text.attributes['font-style'] || 'normal';
    const fontWeight = text.attributes['font-weight'] || 'normal';
    if (utils.parseBooleanChild(this.node, 'AutoSize')) {
      fontSize = this.autoscale(innerText, fontFamily, fontStyle, fontWeight, fontSize);
      text.setAttribute('font-size', fontSize);
    }


    const fm = this.getFontMetrics(innerText, fontFamily, fontStyle, fontWeight, fontSize);

    const vertAlignment = utils.parseStringChild(textStyleNode, 'VerticalAlignment');
    if (vertAlignment === 'CENTER') {
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('y', String(this.y + (this.height / 2)));
    } else if (vertAlignment === 'TOP') {
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('y', String(this.y + (fm.height / 2)));
    } else if (vertAlignment === 'BOTTOM') {
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('y', String(this.y + this.height - (fm.height / 2)));
    }

    return g;
  }

  registerBinding(binding: DataSourceBinding) {
    console.warn('Unsupported binding to property: ' + binding.dynamicProperty);
  }

  private autoscale(text: string, fontFamily: string, fontStyle: string, fontWeight: string, fontSizeStart: string) {
    const fm = this.getFontMetrics(text, fontFamily, fontStyle, fontWeight, fontSizeStart);
    const ptStart = Math.floor(Number(fontSizeStart.replace('pt', '')));
    if (fm.width > this.width || fm.height > this.height) {
      return this.scaleDown(text, fontFamily, fontStyle, fontWeight, ptStart - 1);
    } else {
      return this.scaleUp(text, fontFamily, fontStyle, fontWeight, ptStart);
    }
  }

  private scaleUp(text: string, fontFamily: string, fontStyle: string, fontWeight: string, pt: number) {
    let fm;
    let size = pt;
    while (true) {
      fm = this.getFontMetrics(text, fontFamily, fontStyle, fontWeight, `${size}pt`);
      if (fm.width > this.width || fm.height > this.height) {
        return `${size - 1}pt`;
      } else {
        size++;
      }
    }
  }

  private scaleDown(text: string, fontFamily: string, fontStyle: string, fontWeight: string, pt: number) {
    let fm;
    let size = pt;
    while (true) {
      fm = this.getFontMetrics(text, fontFamily, fontStyle, fontWeight, `${size}pt`);
      if (fm.width <= this.width && fm.height <= this.height) {
        return `${size}pt`;
      } else {
        size--;
      }
    }
  }
}
