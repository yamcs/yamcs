import { NamedObjectId, ParameterValue } from '@yamcs/client';
import { Display } from '../Display';
import { DisplayCommunicator } from '../DisplayCommunicator';
import { NavigationHandler } from '../NavigationHandler';
import { Defs, Pattern, Rect, Svg, Tag } from '../tags';
import { Color } from './Color';
import * as utils from './utils';


export class OpiDisplay implements Display {

  title: string;
  width: number;
  height: number;
  bgcolor: Color;

  container: HTMLDivElement;

  constructor(
    readonly navigationHandler: NavigationHandler,
    private targetEl: HTMLDivElement,
    readonly displayCommunicator: DisplayCommunicator,
  ) {
    this.container = document.createElement('div');
    this.container.setAttribute('style', 'position: relative');
    this.targetEl.appendChild(this.container);
  }

  parseAndDraw(id: string, grid = false) {
    return this.displayCommunicator.getXMLObject('displays', id).then(doc => {
      const displayEl = doc.getElementsByTagName('display')[0];

      this.title = utils.parseStringChild(displayEl, 'name', 'Untitled');
      this.width = utils.parseFloatChild(displayEl, 'width');
      this.height = utils.parseFloatChild(displayEl, 'height');

      const bgNode = utils.findChild(displayEl, 'background_color');
      this.bgcolor = utils.parseColorChild(bgNode, 'color', Color.WHITE);

      const rootEl = new Svg({
        width: this.width,
        height: this.height,
        'xmlns': 'http://www.w3.org/2000/svg',
        'xmlns:xlink': 'http://www.w3.org/1999/xlink',
      });

      this.addDefinitions(rootEl);
      this.addStyles(rootEl);

      rootEl.addChild(new Rect({
        x: 0,
        y: 0,
        width: this.width,
        height: this.height,
        fill: this.bgcolor,
      }));

      // for (const widgetNode of utils.findChildren(displayEl, 'widget')) {
        // console.log('have', widgetNode);
      // }

      const svg = rootEl.toDomElement() as SVGSVGElement;
      this.targetEl.appendChild(svg);
    });
  }

  public getBackgroundColor() {
    return this.bgcolor.toString();
  }

  private addDefinitions(svg: Svg) {
    const defs = new Defs().addChild(
      new Pattern({
        id: 'uss-grid',
        patternUnits: 'userSpaceOnUse',
        width: 10,
        height: 10,
      }).addChild(
        new Rect({ x: 0, y: 0, width: 2, height: 1, fill: '#c0c0c0' })
      )
    );
    svg.addChild(defs);
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
}
