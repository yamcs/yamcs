import { NamedObjectId, ParameterValue } from '@yamcs/client';
import { Display } from '../Display';
import { DisplayCommunicator } from '../DisplayCommunicator';
import { NavigationHandler } from '../NavigationHandler';
import { Defs, Pattern, Rect, Svg, Tag } from '../tags';
import { Color } from './Color';
import * as utils from './utils';
import { AbstractWidget } from './widgets/AbstractWidget';
import { ActionButton } from './widgets/ActionButton';
import { Connection } from './widgets/Connection';
import { Label } from './widgets/Label';
import { LED } from './widgets/LED';
import { LinkingContainer } from './widgets/LinkingContainer';
import { Rectangle } from './widgets/Rectangle';
import { TextUpdate } from './widgets/TextUpdate';

export class OpiDisplay implements Display {

  private widgets: AbstractWidget[] = [];

  title: string;
  width: number;
  height: number;
  bgcolor: Color;

  defs: Defs;

  container: HTMLDivElement;
  measurerSvg: SVGSVGElement;

  constructor(
    readonly navigationHandler: NavigationHandler,
    private targetEl: HTMLDivElement,
    readonly displayCommunicator: DisplayCommunicator,
  ) {
    this.container = document.createElement('div');
    this.container.setAttribute('style', 'position: relative');
    this.targetEl.appendChild(this.container);

    // Invisible SVG used to measure font metrics before drawing
    this.measurerSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    this.measurerSvg.setAttribute('height', '0');
    this.measurerSvg.setAttribute('width', '0');
    this.measurerSvg.setAttribute('style', 'visibility: hidden');
    targetEl.appendChild(this.measurerSvg);
  }

  parseAndDraw(id: string, grid = true) {
    return this.displayCommunicator.getXMLObject('displays', id).then(doc => {
      const displayEl = doc.getElementsByTagName('display')[0];

      this.title = utils.parseStringChild(displayEl, 'name', 'Untitled');
      this.width = utils.parseFloatChild(displayEl, 'width');
      this.height = utils.parseFloatChild(displayEl, 'height');

      const bgNode = utils.findChild(displayEl, 'background_color');
      this.bgcolor = utils.parseColorChild(bgNode, Color.WHITE);

      const rootEl = new Svg({
        width: this.width,
        height: this.height,
        'xmlns': 'http://www.w3.org/2000/svg',
        'xmlns:xlink': 'http://www.w3.org/1999/xlink',
      });

      const gridSpace = utils.parseIntChild(displayEl, 'grid_space');

      const fgNode = utils.findChild(displayEl, 'foreground_color');
      const gridColor = utils.parseColorChild(fgNode);

      this.defs = this.createDefinitions(gridSpace, gridColor);
      rootEl.addChild(this.defs);
      this.addStyles(rootEl);

      rootEl.addChild(new Rect({
        x: 0,
        y: 0,
        width: this.width,
        height: this.height,
        fill: this.bgcolor,
      }));

      if (grid) {
        rootEl.addChild(new Rect({
          x: 0,
          y: 0,
          width: this.width,
          height: this.height,
          style: 'fill: url(#css-grid)',
        }));
      }

      for (const widgetNode of utils.findChildren(displayEl, 'widget')) {
        const widget = this.createWidget(widgetNode);
        if (widget) {
          widget.tag = widget.drawWidget();
          this.addWidget(widget, rootEl);
        }
      }

      for (const connectionNode of utils.findChildren(displayEl, 'connection')) {
        const connection = new Connection(connectionNode, this);
        const tag = connection.draw();
        rootEl.addChild(tag);
      }

      const svg = rootEl.toDomElement() as SVGSVGElement;
      this.targetEl.appendChild(svg);

      // Call widget-specific lifecycle hooks
      for (const widget of this.widgets) {
        widget.svg = svg;
        widget.afterDomAttachment();
        // widget.initializeBindings();
      }
    });
  }

  public getBackgroundColor() {
    return this.bgcolor.toString();
  }

  private createDefinitions(gridSpace: number, gridColor: Color) {
    return new Defs().addChild(
      new Pattern({
        id: 'css-grid',
        patternUnits: 'userSpaceOnUse',
        width: gridSpace,
        height: gridSpace,
      }).addChild(
        new Rect({ x: 0, y: 0, width: 2, height: 1, fill: gridColor.toString() })
      )
    );
  }

  private addStyles(svg: Svg) {
    const style = new Tag('style', {}, `
      text {
        user-select: none;
        -moz-user-select: none;
        -khtml-user-select: none;
        -webkit-user-select: none;
      }
      .field:hover {
        opacity: 0.7;
      }
    `);
    svg.addChild(style);
  }

  createWidget(node: Element): AbstractWidget | undefined {
    const typeId = utils.parseStringAttribute(node, 'typeId');
    switch (typeId) {
      case 'org.csstudio.opibuilder.widgets.ActionButton':
        return new ActionButton(node, this);
      case 'org.csstudio.opibuilder.widgets.Label':
        return new Label(node, this);
      case 'org.csstudio.opibuilder.widgets.LED':
        return new LED(node, this);
      case 'org.csstudio.opibuilder.widgets.linkingContainer':
        return new LinkingContainer(node, this);
      case 'org.csstudio.opibuilder.widgets.Rectangle':
        return new Rectangle(node, this);
      case 'org.csstudio.opibuilder.widgets.TextUpdate':
        return new TextUpdate(node, this);
      default:
        // tslint:disable-next-line:no-console
        console.warn(`Unsupported widget type: ${typeId}`);
    }
  }

  getParameterIds() {
    const ids: NamedObjectId[] = [];
    return ids;
  }

  getDataSourceState() {
    const green = false;
    const yellow = false;
    const red = false;
    return { green, yellow, red };
  }

  processParameterValues(pvals: ParameterValue[]) {
  }

  digest() {
  }

  addWidget(widget: AbstractWidget, parent: Tag) {
    parent.addChild(widget.tag);
    this.widgets.push(widget);
  }

  findWidget(wuid: string) {
    for (const widget of this.widgets) {
      if (widget.wuid === wuid) {
        return widget;
      }
    }
  }
}
