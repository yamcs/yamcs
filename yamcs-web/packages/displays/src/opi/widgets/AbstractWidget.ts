import { Tag } from '../../tags';
import { Color } from '../Color';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';

let widgetSequence = 0;

export abstract class AbstractWidget {

  sequenceNumber: number;

  // Long ID as contained in the display
  wuid: string;

  // Shorter ID for use in SVG
  id: string;

  x: number;
  y: number;
  width: number;
  height: number;
  name: string;
  text: string;

  pvName: string;

  backgroundColor: Color;
  foregroundColor: Color;
  textStyle: { [key: string]: any };
  borderStyle: { [key: string]: any };
  transparent: boolean;
  visible: boolean;
  borderAlarmSensitive: boolean;
  effect3d: boolean;

  svg: SVGSVGElement;
  childSequence = 0;

  tag: Tag;

  constructor(
    protected node: Element,
    protected display: OpiDisplay,
  ) {
    this.sequenceNumber = widgetSequence++;
    this.wuid = utils.parseStringChild(node, 'wuid');
    this.id = `w${this.sequenceNumber}`;
    this.x = utils.parseIntChild(node, 'x');
    this.y = utils.parseIntChild(node, 'y');
    this.width = utils.parseIntChild(node, 'width');
    this.height = utils.parseIntChild(node, 'height');
    this.name = utils.parseStringChild(node, 'name');

    this.text = utils.parseStringChild(node, 'text', '');
    this.text = this.text.split(' ').join('\u00a0'); // Preserve whitespace

    const backgroundColorNode = utils.findChild(node, 'background_color');
    this.backgroundColor = utils.parseColorChild(backgroundColorNode);

    const foregroundColorNode = utils.findChild(node, 'foreground_color');
    this.foregroundColor = utils.parseColorChild(foregroundColorNode);

    const fontNode = utils.findChild(this.node, 'font');
    this.textStyle = utils.parseTextStyle(fontNode);

    this.borderStyle = {};
    const style = utils.parseIntChild(this.node, 'border_style');
    if (style === 0) {
      // No border
    } else if (style === 1) { // Line Style
      const borderColorNode = utils.findChild(this.node, 'border_color');
      const borderColor = utils.parseColorChild(borderColorNode);
      this.borderStyle['stroke'] = borderColor;
      const borderWidth = utils.parseIntChild(this.node, 'border_width');
      this.borderStyle['stroke-width'] = borderWidth;
    } else {
      console.warn(`Unsupported border style: ${style}`);
    }

    this.transparent = utils.parseBooleanChild(this.node, 'transparent', false);
    this.visible = utils.parseBooleanChild(this.node, 'visible');
    this.borderAlarmSensitive = utils.parseBooleanChild(this.node, 'border_alarm_sensitive', false);
    this.effect3d = utils.parseBooleanChild(this.node, 'effect_3d', false);

    if (utils.hasChild(node, 'pv_name')) {
      this.pvName = utils.parseStringChild(node, 'pv_name');
    }
  }

  abstract parseAndDraw(): Tag;

  /**
   * Hook to perform logic after this widget (or any other widget) was added to the DOM.
   */
  afterDomAttachment() {
    // NOP
  }

  protected getFontMetrics(text: string, fontFamily: string, fontStyle: string, fontWeight: string, fontSize: string) {
    const el = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    el.setAttribute('font-family', fontFamily);
    el.setAttribute('font-style', fontStyle);
    el.setAttribute('font-weight', fontWeight);
    el.setAttribute('font-size', fontSize);
    el.appendChild(document.createTextNode(text));
    this.display.measurerSvg.appendChild(el);
    const bbox = el.getBBox();
    this.display.measurerSvg.removeChild(el);
    return { height: bbox.height, width: bbox.width };
  }

  protected generateChildId() {
    const id = `${this.id}c${this.childSequence}`;
    this.childSequence += 1;
    return id;
  }
}
