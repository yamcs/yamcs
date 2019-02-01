import { G, Line, Rect, Text, Tspan } from '../../tags';
import { ExecuteJavaScriptAction, OpenDisplayAction } from '../actions';
import { Color } from '../Color';
import { Font } from '../Font';
import { OpiDisplay } from '../OpiDisplay';
import { Script } from '../scripting/Script';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class ActionButton extends AbstractWidget {

  private font: Font;
  private pushActionIndex: number;

  constructor(node: Element, display: OpiDisplay) {
    super(node, display);
    const fontNode = utils.findChild(this.node, 'font');
    this.font = utils.parseFontNode(fontNode);
    this.pushActionIndex = utils.parseIntChild(node, 'push_action_index');
  }

  draw(g: G) {
    g.setAttribute('cursor', 'pointer');

    g.addChild(new Rect({
      id: `${this.id}-bg`,
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      fill: this.backgroundColor || Color.BUTTON,
    }));

    const top = this.holderY + 0.5;
    const left = this.holderX + 0.5;
    const bottom = this.holderY + this.holderHeight - 1 + 0.5;
    const right = this.holderX + this.holderWidth - 1 + 0.5;
    const shadow1 = Color.BLACK;
    const shadow2 = Color.BUTTON_DARKER;
    const hl1 = Color.BUTTON_LIGHTEST;
    const hl2 = this.backgroundColor || Color.BUTTON;
    g.addChild(
      new Line({
        id: `${this.id}-stroke-1`,
        x1: right, y1: bottom,
        x2: right, y2: top,
        stroke: shadow1,
      }),
      new Line({
        id: `${this.id}-stroke-2`,
        x1: right, y1: bottom,
        x2: left, y2: bottom,
        stroke: shadow1,
      }),
      new Line({
        id: `${this.id}-stroke-3`,
        x1: right - 1, y1: bottom - 1,
        x2: right - 1, y2: top + 1,
        stroke: shadow2,
      }),
      new Line({
        id: `${this.id}-stroke-4`,
        x1: right - 1, y1: bottom - 1,
        x2: left + 1, y2: bottom - 1,
        stroke: shadow2,
      }),
      new Line({
        id: `${this.id}-stroke-5`,
        x1: left, y1: top,
        x2: right - 1, y2: top,
        stroke: hl1,
      }),
      new Line({
        id: `${this.id}-stroke-6`,
        x1: left, y1: top,
        x2: left, y2: bottom - 1,
        stroke: hl1,
      }),
      new Line({
        id: `${this.id}-stroke-7`,
        x1: left + 1, y1: top + 1,
        x2: right - 1 - 1, y2: top + 1,
        stroke: hl2,
      }),
      new Line({
        id: `${this.id}-stroke-8`,
        x1: left + 1, y1: top + 1,
        x2: left + 1, y2: bottom - 1 - 1,
        stroke: hl2,
      }),
    );

    const textG = new G({
      id: `${this.id}-textg`,
      transform: `translate(${this.x} ${this.y})`
    });

    const lines = this.text.split('\n');

    const text = new Text({
      id: `${this.id}-text`,
      x: 0,
      y: 0,
      'pointer-events': 'none',
      ...this.font.getStyle(),
      fill: this.foregroundColor,
    });

    for (let i = 0; i < lines.length; i++) {
      text.addChild(new Tspan({
        x: 0,
        dy: (i === 0) ? '0' : '1.2em',
        'dominant-baseline': 'hanging',
      }, lines[i]));
    }

    textG.addChild(text);
    g.addChild(textG);
  }

  afterDomAttachment() {
    // Align (multiline) text
    const textEl = this.svg.getElementById(`${this.id}-text`) as SVGTextElement;
    const bbox = textEl.getBBox();
    const gEl = this.svg.getElementById(`${this.id}-textg`) as SVGGElement;
    const inset = 2;
    const x = inset + this.x + (this.width - (2 * inset) - bbox.width) / 2;
    const y = inset + this.y + (this.height - (2 * inset) - bbox.height) / 2;
    gEl.setAttribute('transform', `translate(${x} ${y})`);

    const buttonEl = this.svg.getElementById(`${this.id}-bg`) as SVGRectElement;
    const strokeEls: SVGLineElement[] = [];
    for (let i = 0; i < 8; i++) {
      strokeEls[i] = this.svg.getElementById(`${this.id}-stroke-${i + 1}`) as SVGLineElement;
    }

    buttonEl.addEventListener('click', () => {
      const action = this.actions[this.pushActionIndex];
      if (action.type === 'OPEN_DISPLAY') {
        const openDisplayAction = action as OpenDisplayAction;
        const handler = this.display.navigationHandler;
        if (handler) {
          handler.openDisplay({
            target: this.display.resolve(openDisplayAction.path),
            openInNewWindow: openDisplayAction.mode !== 0,
          });
        }
      } else if (action.type === 'EXECUTE_JAVASCRIPT') {
        const executeJavascriptAction = action as ExecuteJavaScriptAction;
        if (executeJavascriptAction.embedded) {
          const script = new Script(this.display, executeJavascriptAction.text!);
          script.run();
        } else {
          const path = this.display.resolve(executeJavascriptAction.path!);
          this.display.displayCommunicator.getObject('displays', path).then(response => {
            response.text().then(text => {
              const script = new Script(this.display, text);
              script.run();
            });
          });
        }
      }
    });
    buttonEl.addEventListener('mousedown', () => {
      strokeEls[0].setAttribute('stroke', Color.BUTTON_LIGHTEST.toString());
      strokeEls[1].setAttribute('stroke', Color.BUTTON_LIGHTEST.toString());
      strokeEls[2].setAttribute('stroke', (this.backgroundColor || Color.BUTTON_LIGHTEST).toString());
      strokeEls[3].setAttribute('stroke', (this.backgroundColor || Color.BUTTON_LIGHTEST).toString());
      strokeEls[4].setAttribute('stroke', Color.BLACK.toString());
      strokeEls[5].setAttribute('stroke', Color.BLACK.toString());
      strokeEls[6].setAttribute('stroke', Color.BUTTON_DARKER.toString());
      strokeEls[7].setAttribute('stroke', Color.BUTTON_DARKER.toString());
      gEl.setAttribute('transform', `translate(${x + 1} ${y + 1})`);
    });
    buttonEl.addEventListener('mouseup', () => {
      strokeEls[0].setAttribute('stroke', Color.BLACK.toString());
      strokeEls[1].setAttribute('stroke', Color.BLACK.toString());
      strokeEls[2].setAttribute('stroke', Color.BUTTON_DARKER.toString());
      strokeEls[3].setAttribute('stroke', Color.BUTTON_DARKER.toString());
      strokeEls[4].setAttribute('stroke', Color.BUTTON_LIGHTEST.toString());
      strokeEls[5].setAttribute('stroke', Color.BUTTON_LIGHTEST.toString());
      strokeEls[6].setAttribute('stroke', (this.backgroundColor || Color.BUTTON_LIGHTEST).toString());
      strokeEls[7].setAttribute('stroke', (this.backgroundColor || Color.BUTTON_LIGHTEST).toString());
      gEl.setAttribute('transform', `translate(${x} ${y})`);
    });
    buttonEl.addEventListener('mouseout', () => {
      strokeEls[0].setAttribute('stroke', Color.BLACK.toString());
      strokeEls[1].setAttribute('stroke', Color.BLACK.toString());
      strokeEls[2].setAttribute('stroke', Color.BUTTON_DARKER.toString());
      strokeEls[3].setAttribute('stroke', Color.BUTTON_DARKER.toString());
      strokeEls[4].setAttribute('stroke', Color.BUTTON_LIGHTEST.toString());
      strokeEls[5].setAttribute('stroke', Color.BUTTON_LIGHTEST.toString());
      strokeEls[6].setAttribute('stroke', (this.backgroundColor || Color.BUTTON_LIGHTEST).toString());
      strokeEls[7].setAttribute('stroke', (this.backgroundColor || Color.BUTTON_LIGHTEST).toString());
      gEl.setAttribute('transform', `translate(${x} ${y})`);
    });
  }
}
