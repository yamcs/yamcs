import * as utils from './utils';

import { ExternalImage } from './widgets/ExternalImage';
import { Field } from './widgets/Field';
import { Label } from './widgets/Label';
import { LinearTickMeter } from './widgets/LinearTickMeter';
// TODO import { LineGraph } from './widgets/LineGraph';
import { NavigationButton } from './widgets/NavigationButton';
import { Polyline } from './widgets/Polyline';
import { Rectangle } from './widgets/Rectangle';
import { Symbol } from './widgets/Symbol';
import { AbstractWidget } from './widgets/AbstractWidget';
import { Parameter } from './Parameter';
import { ParameterBinding } from './ParameterBinding';
import { Svg, Rect, Tag, Defs, Marker, Path, Pattern } from './tags';
import { Compound } from './widgets/Compound';
import { Color } from './Color';
import { ResourceResolver } from './ResourceResolver';

let widgetSequence = 0;

export class Display {

  private widgets: { [key: string]: AbstractWidget } = {};
  parameters: { [key: string]: Parameter } = {};

  bgcolor: Color;
  width: number;
  height: number;

  measurerSvg: SVGSVGElement;

  constructor(private targetEl: HTMLDivElement, public resourceResolver: ResourceResolver) {
    // Invisible SVG used to measure font metrics before drawing
    this.measurerSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    this.measurerSvg.setAttribute('height', '0');
    this.measurerSvg.setAttribute('width', '0');
    this.measurerSvg.setAttribute('style', 'visibility: hidden');
    targetEl.appendChild(this.measurerSvg);
  }

  parseAndDraw(xmlDoc: XMLDocument) {
    const displayEl = xmlDoc.getElementsByTagName('Display')[0];

    this.width = utils.parseFloatChild(displayEl, 'Width');
    this.height = utils.parseFloatChild(displayEl, 'Height');

    const rootEl = new Svg({
      width: this.width,
      height: this.height,
    });

    this.addDefinitions(rootEl);

    // draw background
    this.bgcolor = utils.parseColorChild(displayEl, 'BackgroundColor', new Color(212, 212, 212, 0));

    rootEl.addChild(new Rect({
      x: 0,
      y: 0,
      width: this.width,
      height: this.height,
      fill: this.bgcolor
    }));

    rootEl.addChild(new Rect({
      x: 0,
      y: 0,
      width: this.width,
      height: this.height,
      class: 'uss-grid',
      style: 'fill: url(#uss-grid)',
    }));

    const elementsNode = utils.findChild(displayEl, 'Elements');
    const elementNodes = utils.findChildren(elementsNode);
    this.drawElements(rootEl, elementNodes);

    const svg = rootEl.toDomElement() as SVGSVGElement;
    this.targetEl.appendChild(svg);

    // Call widget-specific lifecycle hooks
    for (const key in this.widgets) {
      if (this.widgets.hasOwnProperty(key)) {
        const widget = this.widgets[key];
        widget.svg = svg;
        widget.afterDomAttachment();
      }
    }
  }

  /**
   * Creates a definition section in the SVG and adds the markers that will
   * be used for polylines arrows.
   *
   * TODO: It is broken currently because the markers will show all in black,
   * instead of the color of the line
   */
  private addDefinitions(svg: Svg) {
    const defs = new Defs().addChild(
      new Marker({
        id: 'uss-arrowStart',
        refX: 0,
        refY: 0,
        markerWidth: 20,
        markerHeight: 20,
        orient: 'auto',
        style: 'overflow: visible; fill: currentColor; stroke: none',
      }).addChild(new Path({
        d: 'M0,-15 l-20,0 l0,15',
        transform: 'scale(0.2, 0.2) translate(20, 0)',
        'fill-rule': 'evenodd',
        'fill-opacity': '1.0',
      })),
      new Marker({
        id: 'uss-arrowEnd',
        refX: 0,
        refY: 0,
        markerWidth: 20,
        markerHeight: 20,
        orient: 'auto',
        style: 'overflow: visible; fill: currentColor; stroke: none',
      }).addChild(new Path({
        d: 'M0,-15 l-20,0 l0,15',
        transform: 'scale(0.2, 0.2) rotate(180) translate(20, 0)',
        'fill-rule': 'evenodd',
        'fill-opacity': '1.0',
      })),
      new Pattern({
        id: 'uss-grid',
        patternUnits: 'userSpaceOnUse',
        width: 10,
        height: 10,
      }).addChild(
        new Rect({ x: 0, y: 0, width: 1, height: 1, fill: 'white' })
      )
    );

    svg.addChild(defs);
  }

  drawElements(parent: Tag, elementNodes: Node[]) {
    for (let i = 0; i < elementNodes.length; i++) {
      const node = elementNodes[i];
      if (node.attributes.getNamedItem('reference')) {
        elementNodes[i] = utils.getReferencedElement(node);
        console.log('resolved a reference ', elementNodes[i]);
      }
    }

    const widgets = [];
    for (const node of elementNodes) {
      const widget = this.parseAndDrawWidget(node);
      if (widget) {
        widgets.push(widget);
      }
    }

    // Widgets are added by depth first, and by definition order second.
    widgets.sort((a, b) => {
      const cmp = a.depth - b.depth;
      return cmp || (a.sequenceNumber - b.sequenceNumber);
    });

    for (const widget of widgets) {
      this.addWidget(widget, parent);
    }
  }

  parseAndDrawWidget(node: Node) {
    return this.parseAndDrawWidgetByName(node, node.nodeName);
  }

  private parseAndDrawWidgetByName(node: Node, widgetName: string): AbstractWidget | undefined {
    widgetSequence += 1;
    switch (widgetName) {
      case 'Compound':
        return new Compound(widgetSequence, node, this);
      case 'ExternalImage':
        return new ExternalImage(widgetSequence, node, this);
      case 'Field':
        return new Field(widgetSequence, node, this);
      case 'Label':
        return new Label(widgetSequence, node, this);
      case 'LinearTickMeter':
        return new LinearTickMeter(widgetSequence, node, this);
      /// case 'LineGraph':
      /// TODO return new LineGraph(widgetSequence, node, this);
      case 'NavigationButton':
        return new NavigationButton(widgetSequence, node, this);
      case 'Polyline':
        return new Polyline(widgetSequence, node, this);
      case 'Rectangle':
        return new Rectangle(widgetSequence, node, this);
      case 'Symbol':
        return new Symbol(widgetSequence, node, this);
      case 'LabelFor':
        const widgetClass = utils.parseStringAttribute(node, 'class');
        return this.parseAndDrawWidgetByName(node, widgetClass);
      default:
        console.warn(`Unsupported widget type: ${widgetName}`);
        return;
    }
  }

  addWidget(widget: AbstractWidget, parent: Tag) {
    parent.addChild(widget.tag);
    this.widgets[widget.id] = widget;
    this.registerDataBindings(widget);
  }

  private registerDataBindings(w: AbstractWidget) {
    if (w.dataBindings.length > 0) {
      for (const dataBinding of w.dataBindings) {
        let para = this.parameters[dataBinding.opsname];
        if (!para) {
          para = new Parameter();
          para.name = dataBinding.opsname;
          para.type = dataBinding.type;

          if (para.type === 'Computation') {
            para.expression = dataBinding.expression;
            para.args = dataBinding.args;
          }
          this.parameters[dataBinding.opsname] = para;
        }

        const dynamicProperty = dataBinding.dynamicProperty;
        const usingRaw = dataBinding.usingRaw || false;
        para.bindings.push(new ParameterBinding(w, dynamicProperty, usingRaw));
      }
    }
  }

  getParameters() {
    const result = [];
    for (const paraname of Object.keys(this.parameters)) {
      const parameter = this.parameters[paraname];
      if (parameter.type === 'ExternalDataSource') {
        result.push(parameter);
      }
    }
    return result;
  }

  updateBindings(pvals: any) {
    for (const pval of pvals) {
      const dbs = this.parameters[pval.id.name];
      if (dbs && dbs.bindings) {
        for (const binding of dbs.bindings) {
          binding.updateWidget(pval);
        }
      }
    }
  }

  getComputations() {
    const result = [];
    for (const paraname of Object.keys(this.parameters)) {
      const parameter = this.parameters[paraname];
      if (parameter.type === 'Computation') {
        result.push(parameter);
      }
    }
    return result;
  }
}
