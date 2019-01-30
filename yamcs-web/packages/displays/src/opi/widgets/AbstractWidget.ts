import { ParameterValue } from '@yamcs/client';
import { G, Line, Mask, Rect, Tag, Text } from '../../tags';
import { Action, OpenDisplayAction } from '../actions';
import { Color } from '../Color';
import { Font } from '../Font';
import { OpiDisplay, TYPE_RECTANGLE, TYPE_ROUNDED_RECTANGLE } from '../OpiDisplay';
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

  insets: [number, number, number, number]; // T L B R

  // bbox around the widget (excluding border)
  x: number;
  y: number;
  width: number;
  height: number;

  typeId: string;
  type: string;
  name: string;
  text: string;

  pvName: string;

  // Marks if the model was updated since the last UI render
  dirty = false;

  backgroundColor: Color;
  foregroundColor: Color;

  transparent: boolean;
  visible: boolean;

  actions: Action[] = [];

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
    this.typeId = utils.parseStringAttribute(node, 'typeId');
    this.type = utils.parseStringChild(node, 'widget_type');
    this.name = utils.parseStringChild(node, 'name');

    this.holderX = utils.parseIntChild(node, 'x');
    this.holderY = utils.parseIntChild(node, 'y');
    this.holderWidth = utils.parseIntChild(node, 'width');
    this.holderHeight = utils.parseIntChild(node, 'height');

    const borderColorNode = utils.findChild(node, 'border_color');
    this.borderColor = utils.parseColorChild(borderColorNode);
    this.borderWidth = utils.parseIntChild(node, 'border_width');
    this.borderStyle = utils.parseIntChild(node, 'border_style');
    this.borderAlarmSensitive = utils.parseBooleanChild(node, 'border_alarm_sensitive', false);

    this.insets = [0, 0, 0, 0];
    switch (this.borderStyle) {
      case 0: // Empty
        if (this.borderAlarmSensitive) {
          this.insets = [2, 2, 2, 2];
        }
        break;
      case 1: // Line
        this.insets = [this.borderWidth, this.borderWidth, this.borderWidth, this.borderWidth];
        break;
      case 2: // Raised
      case 3: // Lowered
        this.insets = [1, 1, 1, 1];
        break;
      case 4: // Etched
      case 5: // Ridged
      case 6: // Button Raised
        this.insets = [2, 2, 2, 2];
        break;
      case 7: // Button Pressed
        this.insets = [2, 2, 1, 1];
        break;
      case 8: // Dot
      case 9: // Dash
      case 10: // Dash Dot
      case 11: // Dash Dot Dot
        this.insets = [this.borderWidth, this.borderWidth, this.borderWidth, this.borderWidth];
        break;
      case 12: // Title Bar
        this.insets = [16 + 1, 1, 1, 1];
        break;
      case 13: // Group Box
        this.insets = [16, 16, 16, 16];
        break;
      case 14: // Round Rectangle Background
        const i = this.borderWidth * 2;
        this.insets = [i, i, i, i];
        break;
    }

    // Shrink the availabe widget area
    this.x = this.holderX + this.insets[1];
    this.y = this.holderY + this.insets[0];
    this.width = this.holderWidth - this.insets[1] - this.insets[3];
    this.height = this.holderHeight - this.insets[0] - this.insets[2];

    this.text = utils.parseStringChild(node, 'text', '');
    this.text = this.text.split(' ').join('\u00a0'); // Preserve whitespace

    if (utils.hasChild(node, 'background_color')) {
      const backgroundColorNode = utils.findChild(node, 'background_color');
      this.backgroundColor = utils.parseColorChild(backgroundColorNode);
    }

    const foregroundColorNode = utils.findChild(node, 'foreground_color');
    this.foregroundColor = utils.parseColorChild(foregroundColorNode);

    this.transparent = utils.parseBooleanChild(node, 'transparent', false);
    this.visible = utils.parseBooleanChild(node, 'visible');

    if (utils.hasChild(node, 'pv_name')) {
      this.pvName = utils.parseStringChild(node, 'pv_name');
    }

    if (utils.hasChild(node, 'actions')) {
      const actionsNode = utils.findChild(node, 'actions');
      for (const actionNode of utils.findChildren(actionsNode, 'action')) {
        const actionType = utils.parseStringAttribute(actionNode, 'type');
        if (actionType === 'OPEN_DISPLAY') {
          const action: OpenDisplayAction = {
            type: actionType,
            path: utils.parseStringChild(actionNode, 'path'),
            mode: utils.parseIntChild(actionNode, 'mode'),
          };
          this.actions.push(action);
        } else {
          console.warn(`Unsupported action type ${actionType}`);
          this.actions.push({ type: actionType });
        }
      }
    }
  }

  drawWidget() {
    const g = new G({
      id: this.id,
      class: this.type.replace(' ', '-').toLowerCase(),
      'data-name': this.name,
    });

    if (this.borderStyle === 0) { // No border
      // This is a weird one. When there is no border the widget
      // shrinks according to an inset of 2px. This only happens when
      // the border is alarm-sensitive.
      if (this.borderAlarmSensitive) {
        g.addChild(new Rect({
          'pointer-events': 'none',
          'fill-opacity': 0,
          'stroke-width': 2,
          // 'shape-rendering': 'crispEdges',
        }).withBorderBox(this.holderX, this.holderY, this.holderWidth, this.holderHeight));
      }
    } else if (this.borderStyle === 1) { // Line
      g.addChild(new Rect({
        'pointer-events': 'none',
        'fill-opacity': '0',
        stroke: this.borderColor,
        'stroke-width': this.borderWidth,
        // 'shape-rendering': 'crispEdges',
      }).withBorderBox(this.holderX, this.holderY, this.holderWidth, this.holderHeight));
    } else if (this.borderStyle === 2) { // Raised
      const top = this.holderY + 0.5;
      const left = this.holderX + 0.5;
      const bottom = this.holderY + this.holderHeight - 1 + 0.5;
      const right = this.holderX + this.holderWidth - 1 + 0.5;
      const shadow = Color.BLACK;
      const hl = Color.WHITE;
      g.addChild(
        new Line({ x1: right, y1: bottom, x2: right, y2: top, stroke: shadow }),
        new Line({ x1: right, y1: bottom, x2: left, y2: bottom, stroke: shadow }),
        new Line({ x1: left, y1: top, x2: right - 1, y2: top, stroke: hl }),
        new Line({ x1: left, y1: top, x2: left, y2: bottom - 1, stroke: hl }),
      );
    } else if (this.borderStyle === 3) { // Lowered
      const top = this.holderY + 0.5;
      const left = this.holderX + 0.5;
      const bottom = this.holderY + this.holderHeight - 1 + 0.5;
      const right = this.holderX + this.holderWidth - 1 + 0.5;
      const shadow = Color.WHITE;
      const hl = Color.BLACK;
      g.addChild(
        new Line({ x1: right, y1: bottom, x2: right, y2: top, stroke: shadow }),
        new Line({ x1: right, y1: bottom, x2: left, y2: bottom, stroke: shadow }),
        new Line({ x1: left, y1: top, x2: right - 1, y2: top, stroke: hl }),
        new Line({ x1: left, y1: top, x2: left, y2: bottom - 1, stroke: hl }),
      );
    } else if (this.borderStyle === 4) { // Etched
      const top = this.holderY + 0.5;
      const left = this.holderX + 0.5;
      const bottom = this.holderY + this.holderHeight - 1 + 0.5;
      const right = this.holderX + this.holderWidth - 1 + 0.5;
      const shadow1 = Color.BUTTON_LIGHTEST;
      const shadow2 = Color.BUTTON_DARKER;
      const hl1 = Color.BUTTON_DARKER;
      const hl2 = Color.BUTTON_LIGHTEST;
      g.addChild(
        new Line({ x1: right, y1: bottom, x2: right, y2: top, stroke: shadow1 }),
        new Line({ x1: right, y1: bottom, x2: left, y2: bottom, stroke: shadow1 }),
        new Line({ x1: right - 1, y1: bottom - 1, x2: right - 1, y2: top + 1, stroke: shadow2 }),
        new Line({ x1: right - 1, y1: bottom - 1, x2: left + 1, y2: bottom - 1, stroke: shadow2 }),
        new Line({ x1: left, y1: top, x2: right - 1, y2: top, stroke: hl1 }),
        new Line({ x1: left, y1: top, x2: left, y2: bottom - 1, stroke: hl1 }),
        new Line({ x1: left + 1, y1: top + 1, x2: right - 1 - 1, y2: top + 1, stroke: hl2 }),
        new Line({ x1: left + 1, y1: top + 1, x2: left + 1, y2: bottom - 1 - 1, stroke: hl2 }),
      );
    } else if (this.borderStyle === 5) { // Ridged
      const top = this.holderY + 0.5;
      const left = this.holderX + 0.5;
      const bottom = this.holderY + this.holderHeight - 1 + 0.5;
      const right = this.holderX + this.holderWidth - 1 + 0.5;
      const shadow1 = Color.BUTTON_DARKER;
      const shadow2 = Color.BUTTON_LIGHTEST;
      const hl1 = Color.BUTTON_LIGHTEST;
      const hl2 = Color.BUTTON_DARKER;
      g.addChild(
        new Line({ x1: right, y1: bottom, x2: right, y2: top, stroke: shadow1 }),
        new Line({ x1: right, y1: bottom, x2: left, y2: bottom, stroke: shadow1 }),
        new Line({ x1: right - 1, y1: bottom - 1, x2: right - 1, y2: top + 1, stroke: shadow2 }),
        new Line({ x1: right - 1, y1: bottom - 1, x2: left + 1, y2: bottom - 1, stroke: shadow2 }),
        new Line({ x1: left, y1: top, x2: right - 1, y2: top, stroke: hl1 }),
        new Line({ x1: left, y1: top, x2: left, y2: bottom - 1, stroke: hl1 }),
        new Line({ x1: left + 1, y1: top + 1, x2: right - 1 - 1, y2: top + 1, stroke: hl2 }),
        new Line({ x1: left + 1, y1: top + 1, x2: left + 1, y2: bottom - 1 - 1, stroke: hl2 }),
      );
    } else if (this.borderStyle === 6) { // Button Raised
      const top = this.holderY + 0.5;
      const left = this.holderX + 0.5;
      const bottom = this.holderY + this.holderHeight - 1 + 0.5;
      const right = this.holderX + this.holderWidth - 1 + 0.5;
      const shadow1 = Color.BUTTON_DARKEST;
      const shadow2 = Color.BUTTON_DARKER;
      const hl1 = Color.BUTTON;
      const hl2 = Color.BUTTON_LIGHTEST;
      g.addChild(
        new Line({ x1: right, y1: bottom, x2: right, y2: top, stroke: shadow1 }),
        new Line({ x1: right, y1: bottom, x2: left, y2: bottom, stroke: shadow1 }),
        new Line({ x1: right - 1, y1: bottom - 1, x2: right - 1, y2: top + 1, stroke: shadow2 }),
        new Line({ x1: right - 1, y1: bottom - 1, x2: left + 1, y2: bottom - 1, stroke: shadow2 }),
        new Line({ x1: left, y1: top, x2: right - 1, y2: top, stroke: hl1 }),
        new Line({ x1: left, y1: top, x2: left, y2: bottom - 1, stroke: hl1 }),
        new Line({ x1: left + 1, y1: top + 1, x2: right - 1 - 1, y2: top + 1, stroke: hl2 }),
        new Line({ x1: left + 1, y1: top + 1, x2: left + 1, y2: bottom - 1 - 1, stroke: hl2 }),
      );
    } else if (this.borderStyle === 7) { // Button Pressed
      const top = this.holderY + 0.5;
      const left = this.holderX + 0.5;
      const bottom = this.holderY + this.holderHeight - 1 + 0.5;
      const right = this.holderX + this.holderWidth - 1 + 0.5;
      const shadow = Color.BUTTON_LIGHTEST;
      const hl1 = Color.BUTTON_DARKEST;
      const hl2 = Color.BUTTON_DARKER;
      g.addChild(
        new Line({ x1: right, y1: bottom, x2: right, y2: top, stroke: shadow }),
        new Line({ x1: right, y1: bottom, x2: left, y2: bottom, stroke: shadow }),
        new Line({ x1: left, y1: top, x2: right - 1, y2: top, stroke: hl1 }),
        new Line({ x1: left, y1: top, x2: left, y2: bottom - 1, stroke: hl1 }),
        new Line({ x1: left + 1, y1: top + 1, x2: right - 1 - 1, y2: top + 1, stroke: hl2 }),
        new Line({ x1: left + 1, y1: top + 1, x2: left + 1, y2: bottom - 1 - 1, stroke: hl2 }),
      );
    } else if (this.borderStyle === 8) { // Dot
      g.addChild(new Rect({
        'pointer-events': 'none',
        'fill-opacity': '0',
        stroke: this.borderColor,
        'stroke-width': this.borderWidth,
        'stroke-dasharray': '2 2',
      }).withBorderBox(this.holderX, this.holderY, this.holderWidth, this.holderHeight));
    } else if (this.borderStyle === 9) { // Dash
      g.addChild(new Rect({
        'pointer-events': 'none',
        'fill-opacity': '0',
        stroke: this.borderColor,
        'stroke-width': this.borderWidth,
        'stroke-dasharray': '6 2',
      }).withBorderBox(this.holderX, this.holderY, this.holderWidth, this.holderHeight));
    } else if (this.borderStyle === 10) { // Dash Dot
      g.addChild(new Rect({
        'pointer-events': 'none',
        'fill-opacity': '0',
        stroke: this.borderColor,
        'stroke-width': this.borderWidth,
        'stroke-dasharray': '6 2 2 2',
      }).withBorderBox(this.holderX, this.holderY, this.holderWidth, this.holderHeight));
    } else if (this.borderStyle === 11) { // Dash Dot Dot
      g.addChild(new Rect({
        'pointer-events': 'none',
        'fill-opacity': '0',
        stroke: this.borderColor,
        'stroke-width': this.borderWidth,
        'stroke-dasharray': '6 2 2 2 2 2',
      }).withBorderBox(this.holderX, this.holderY, this.holderWidth, this.holderHeight));
    } else if (this.borderStyle === 12) { // Title Bar
      g.addChild(new Rect({
        x: this.holderX,
        y: this.holderY + 1,
        width: this.holderWidth,
        height: 16,
        fill: this.borderColor,
      }));
      g.addChild(new Text({
        x: this.holderX + 1 + 3,
        y: this.holderY + 1 + (16 / 2),
        'dominant-baseline': 'middle',
        'text-anchor': 'start',
        'font-size': 11,
      }, this.name));
      g.addChild(new Rect({
        'pointer-events': 'none',
        fill: 'none',
        stroke: Color.BLACK,
        'stroke-width': 1,
      }).withBorderBox(this.holderX, this.holderY, this.holderWidth, this.holderHeight));
    } else if (this.borderStyle === 13) { // Group Box
      if (!this.transparent) {
        g.addChild(new Rect({
          x: this.holderX,
          y: this.holderY,
          width: this.holderWidth,
          height: this.holderHeight,
          fill: this.backgroundColor,
        }));
      }
      const fm = this.getFontMetrics(this.name, Font.ARIAL_11);
      g.addChild(new Mask({
        id: `${this.id}-hide-title-stroke`,
      }).addChild(new Rect({
        x: this.holderX,
        y: this.holderY,
        width: this.holderWidth,
        height: this.holderHeight,
        fill: 'white',
      }), new Rect({
        x: this.holderX + 16,
        y: this.holderY + 8,
        width: fm.width,
        height: 2,
        fill: 'black',
      })));

      g.addChild(new Rect({
        fill: 'none',
        stroke: this.backgroundColor.darker(),
        'stroke-width': 1,
        mask: `url(#${this.id}-hide-title-stroke)`,
      }).withBorderBox(this.holderX + 8, this.holderY + 8, this.holderWidth - 16 - 1, this.holderHeight - 16 - 1));
      g.addChild(new Rect({
        fill: 'none',
        stroke: this.backgroundColor.brighter(),
        'stroke-width': 1,
        mask: `url(#${this.id}-hide-title-stroke)`,
      }).withBorderBox(this.holderX + 8 + 1, this.holderY + 8 + 1, this.holderWidth - 16 - 1, this.holderHeight - 16 - 1));
      g.addChild(new Text({
        x: this.holderX + 16,
        y: this.holderY + 8,
        'text-anchor': 'start',
        'dominant-baseline': 'middle',
        ...Font.ARIAL_11.getStyle(),
        fill: this.borderColor,
      }, this.name));
    } else if (this.borderStyle === 14) { // Round Rectangle Background
      let fillOpacity = 1;
      if (this.typeId === TYPE_RECTANGLE || this.typeId === TYPE_ROUNDED_RECTANGLE) {
        fillOpacity = 0; // Then nested widget appears to decide
      }
      g.addChild(new Rect({
        'pointer-events': 'none',
        fill: this.backgroundColor,
        stroke: this.borderColor,
        'stroke-width': this.borderWidth,
        'fill-opacity': fillOpacity,
        rx: 4,
        ry: 4,
      }).withBorderBox(this.holderX, this.holderY, this.holderWidth, this.holderHeight));
    } else {
      console.warn(`Unsupported border style: ${this.borderStyle}`);
    }

    this.draw(g);
    return g;
  }

  abstract draw(g: G): void;

  /**
   * Hook to perform logic after this widget (or any other widget) was added to the DOM.
   */
  afterDomAttachment() {
    // NOP
  }

  digest() {
    // NOP
  }

  onDelivery(pvals: ParameterValue[]) {
    // NOP
  }

  onParameterValue(pval: ParameterValue) {
    // NOP
  }

  protected getFontMetrics(text: string, font: Font) {
    const style = font.getStyle();
    const el = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    el.setAttribute('font-family', style['font-family']);
    el.setAttribute('font-style', style['font-style'] || 'normal');
    el.setAttribute('font-weight', style['font-weight'] || 'normal');
    el.setAttribute('font-size', style['font-size']);
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
