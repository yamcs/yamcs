import * as utils from './utils';

import { ExternalImage } from './widgets/ExternalImage';
import { Field } from './widgets/Field';
import { Label } from './widgets/Label';
import { LinearTickMeter } from './widgets/LinearTickMeter';
import { LineGraph } from './widgets/LineGraph';
import { NavigationButton } from './widgets/NavigationButton';
import { Polyline } from './widgets/Polyline';
import { Rectangle } from './widgets/Rectangle';
import { Symbol } from './widgets/Symbol';
import { AbstractWidget } from './widgets/AbstractWidget';
import { Parameter } from './Parameter';

let widgetSequence = 0;

export class Display {
  widgets: {[key: string]: AbstractWidget} = {};
  parameters: {[key: string]: Parameter} = {};

  bgcolor: string;
  width: number;
  height: number;

  constructor(private div: HTMLDivElement) {
  }

  parseAndDraw(xmlDoc: XMLDocument) {
    const displayEl = xmlDoc.getElementsByTagName('Display')[0];

    this.width = utils.parseIntChild(displayEl, 'Width');
    this.height = utils.parseIntChild(displayEl, 'Height');

    const svg = $(this.div).svg('get');
    svg.configure({
        height: this.height,
        width: this.width,
        class: 'canvas',
        'xmlns': 'http://www.w3.org/2000/svg',
        'xmlns:xlink': 'http://www.w3.org/1999/xlink'
    });

    utils.addArrowMarkers(svg);

    // draw background
    this.bgcolor = utils.parseColorChild(displayEl, 'BackgroundColor', '#D4D4D4');

    svg.rect(0, 0, this.width, this.height, { fill: this.bgcolor });

    const elementsNode = utils.findChild(displayEl, 'Elements');
    const elementNodes = utils.findChildren(elementsNode);
    this.drawElements(svg, null, elementNodes);
  }

  drawElements(svg: any, parent: any, elementNodes: Node[]) {
    // sort element such that they are drawn in order of their Depth
    // TODO: those that have the same Depth have to still be sorted according to some TBD behaviour
    for (let i = 0; i < elementNodes.length; i++) {
      const e = elementNodes[i];
      if (e.hasAttribute('reference')) {
        elementNodes[i] = utils.getReferencedElement(e);
      }
    }
    elementNodes.sort((a, b) => {
      const da = utils.parseIntChild(a, 'Depth');
      const db = utils.parseIntChild(b, 'Depth');
      return da - db;
    });
    for (const node of elementNodes) {
      this.drawWidget(svg, parent, node);
    }
  }

  drawWidget(svg: any, parent: any, e: Node) {
    const opts = this.parseStandardOptions(e);

    if (e.nodeName === 'Compound') {
      // this corresponds to the group feature of USS. We make a SVG group.
      const g = svg.group(parent, opts.id);
      const elementsNode = utils.findChild(e, 'Elements');
      const elementNodes = utils.findChildren(elementsNode);
      this.drawElements(svg, g, elementNodes);
    } else {
      let w: AbstractWidget;
      switch (e.nodeName) {
        case 'ExternalImage':
          w = new ExternalImage();
          break;
        case 'Field':
          w = new Field();
          break;
        case 'Label':
          w = new Label();
          break;
        case 'LinearTickMeter':
          w = new LinearTickMeter();
          break;
        case 'LineGraph':
          w = new LineGraph();
          break;
        case 'NavigationButton':
          w = new NavigationButton();
          break;
        case 'Polyline':
          w = new Polyline();
          break;
        case 'Rectangle':
          w = new Rectangle();
          break;
        case 'Symbol':
          w = new Symbol();
          break;
        default:
          console.warn('Unsupported widget type: ' + e.nodeName);
          return;
      }

      // make the standard properties part of the object
      w.id = 'w' + widgetSequence;
      widgetSequence += 1;
      w.x = opts.x;
      w.y = opts.y;
      w.width = opts.width;
      w.height = opts.height;
      w.dataBindings = opts.dataBindings;
      w.svg = svg;

      w.parseAndDraw(svg, parent, e);
      const len = opts.dataBindings.length;
      if (len > 0) {
        this.widgets[w.id] = w; // only remember widgets with dynamic properties
        for (const dataBinding of opts.dataBindings) {
          let para = this.parameters[dataBinding.parameterName];
          if (!para) {
            para = new Parameter();
            para.namespace = dataBinding.parameterNamespace;
            para.name = dataBinding.parameterName;
            para.type = dataBinding.type;

            if (para.type === 'Computation') {
              para.expression = dataBinding.expression;
              para.args = dataBinding.args;
            }
            this.parameters[dataBinding.parameterName] = para;
          }
          const binding = {
            dynamicProperty: dataBinding.dynamicProperty,
            widget: w,
            updateWidget: function(para: Parameter) {
              switch (this.dynamicProperty) {
                case 'VALUE':
                  if (this.widget.updateValue === undefined) {
                    return;
                  }
                  this.widget.updateValue(para, this.usingRaw);
                  break;
                case 'X':
                  this.widget.updatePosition(para, 'x', this.usingRaw);
                  break;
                case 'Y':
                  this.widget.updatePosition(para, 'y', this.usingRaw);
                  break;
                case 'FILL_COLOR':
                  this.widget.updateFillColor(para, this.usingRaw);
                  break;
                default:
                  console.warn('Unsupported dynamic property: ' + this.dynamicProperty);
              }
            }
          };

          if (dataBinding.usingRaw !== undefined) {
            binding.usingRaw = dataBinding.usingRaw;
          }

          para.bindings.push(binding);
        }
      }
    }
  }

  private parseStandardOptions(node: Node) {
    const x = utils.parseIntChild(node, 'X');
    const y = utils.parseIntChild(node, 'Y');
    const width = utils.parseIntChild(node, 'Width');
    const height = utils.parseIntChild(node, 'Height');
    const id = utils.parseStringChild(node, 'Name');

    const dataBindings = [];
    const dataBindingsNode = utils.findChild(node, 'DataBindings');
    for (const childNode of utils.findChildren(dataBindingsNode, 'DataBinding')) {
      const dataBinding = utils.parseDataBinding(childNode);
      if (dataBinding) {
        dataBindings.push(dataBinding);
      }
    }

    return { x, y, width, height, id, dataBindings };
  }

  getParameters() {
    const paraList = [];
    for (const paraname of this.parameters) {
      const p = this.parameters[paraname];
      if (p.type === 'ExternalDataSource') {
        paraList.push({name: p.name, namespace: p.namespace});
      }
    }
    return paraList;
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
    const compDefList = [];
    for (const paraname of this.parameters) {
      const p = this.parameters[paraname];
      if (p.type === 'Computation') {
        const cdef = {
          name: paraname,
          expression: p.expression,
          argument: [],
          language: 'jformula'
        };
        for (const arg of p.args) {
          cdef.argument.push({
            name: arg.Opsname,
            namespace: 'MDB:OPS Name'
          });
        }
        compDefList.push(cdef);
      }
    }
    return compDefList;
  }
}
