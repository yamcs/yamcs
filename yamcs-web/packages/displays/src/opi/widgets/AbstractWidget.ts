import { G, Rect, Tag } from '../../tags';
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

  // bbox around the widget and its border
  holderX: number;
  holderY: number;
  holderWidth: number;
  holderHeight: number;

  borderStyle: number;
  borderColor: Color;
  borderWidth: number;
  borderAlarmSensitive: boolean;

  inset: number;

  // bbox around the widget (excluding border)
  x: number;
  y: number;
  width: number;
  height: number;

  widgetType: string;
  name: string;
  text: string;

  pvName: string;

  backgroundColor: Color;
  foregroundColor: Color;
  textStyle: { [key: string]: any };
  transparent: boolean;
  visible: boolean;
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
    this.widgetType = utils.parseStringChild(node, 'widget_type');
    this.name = utils.parseStringChild(node, 'name');

    this.holderX = utils.parseIntChild(node, 'x');
    this.holderY = utils.parseIntChild(node, 'y');
    this.holderWidth = utils.parseIntChild(node, 'width');
    this.holderHeight = utils.parseIntChild(node, 'height');

    const borderColorNode = utils.findChild(this.node, 'border_color');
    this.borderColor = utils.parseColorChild(borderColorNode);
    this.borderWidth = utils.parseIntChild(this.node, 'border_width');
    this.borderStyle = utils.parseIntChild(this.node, 'border_style');
    this.borderAlarmSensitive = utils.parseBooleanChild(this.node, 'border_alarm_sensitive', false);

    this.inset = 0;
    if (this.borderStyle === 0) { // Empty
      if (this.borderAlarmSensitive) {
        this.inset = 2;
      }
    } else if (this.borderStyle === 1) { // Line
      this.inset = 1;
    }

    // Shrink the availabe widget area
    this.x = this.holderX + this.inset;
    this.y = this.holderY + this.inset;
    this.width = this.holderWidth - (2 * this.inset);
    this.height = this.holderHeight - (2 * this.inset);

    this.text = utils.parseStringChild(node, 'text', '');
    this.text = this.text.split(' ').join('\u00a0'); // Preserve whitespace

    const backgroundColorNode = utils.findChild(node, 'background_color');
    this.backgroundColor = utils.parseColorChild(backgroundColorNode);

    const foregroundColorNode = utils.findChild(node, 'foreground_color');
    this.foregroundColor = utils.parseColorChild(foregroundColorNode);

    const fontNode = utils.findChild(this.node, 'font');
    this.textStyle = utils.parseTextStyle(fontNode);

    this.transparent = utils.parseBooleanChild(this.node, 'transparent', false);
    this.visible = utils.parseBooleanChild(this.node, 'visible');
    this.effect3d = utils.parseBooleanChild(this.node, 'effect_3d', false);

    if (utils.hasChild(node, 'pv_name')) {
      this.pvName = utils.parseStringChild(node, 'pv_name');
    }
  }

  drawWidget() {
    const g = new G({
      id: this.id,
      class: this.widgetType.replace(' ', '-'),
      'data-name': this.name,
    });

    const strokeOptions: {[key: string]: any} = {};
    if (this.borderStyle === 0) { // No border
      // This is a weird one. When there is no border the widget
      // shrinks according to an inset of 2px. This only happens when
      // the border is alarm-sensitive.
      if (this.borderAlarmSensitive) {
        strokeOptions['stroke-width'] = 2;
      }
    } else if (this.borderStyle === 1) { // Line Style
      strokeOptions['stroke'] = this.borderColor;
      strokeOptions['stroke-width'] = this.borderWidth;
    } else {
      console.warn(`Unsupported border style: ${this.borderStyle}`);
    }

    const holder = new Rect({
      'pointer-events': 'none',
      'fill-opacity': '0',
      ...strokeOptions,
      // 'shape-rendering': 'crispEdges',
    }).withBorderBox(this.holderX, this.holderY, this.holderWidth, this.holderHeight);

    g.addChild(holder);

    this.parseAndDraw(g);
    return g;
  }

  abstract parseAndDraw(g: G): void;

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
