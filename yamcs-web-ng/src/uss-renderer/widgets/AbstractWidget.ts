import { Tag } from '../tags';
import * as utils from '../utils';
import { Display } from '../Display';
import { ParameterUpdate } from '../ParameterUpdate';
import { ParameterBinding } from '../ParameterBinding';
import { ComputationBinding } from '../ComputationBinding';

let widgetSequence = 0;

export abstract class AbstractWidget {

  sequenceNumber: number;

  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  depth: number;
  name: string;

  parameterBindings: ParameterBinding[];
  computationBindings: ComputationBinding[];

  svg: SVGSVGElement;
  childSequence = 0;

  tag: Tag;

  constructor(
    protected node: Node,
    protected display: Display) {

    this.sequenceNumber = widgetSequence++;
    this.id = `w${this.sequenceNumber}`;
    this.x = utils.parseFloatChild(node, 'X');
    this.y = utils.parseFloatChild(node, 'Y');
    this.width = utils.parseFloatChild(node, 'Width');
    this.height = utils.parseFloatChild(node, 'Height');
    this.depth = utils.parseIntChild(node, 'Depth');
    this.name = utils.parseStringChild(node, 'Name');

    this.parameterBindings = [];
    this.computationBindings = [];
    const dataBindingsNode = utils.findChild(node, 'DataBindings');
    for (const childNode of utils.findChildren(dataBindingsNode, 'DataBinding')) {
      this.parseDataBinding(childNode);
    }
  }

  abstract parseAndDraw(): Tag;

  /**
   * Hook to perform logic after this widget (or any other widget) was added to the DOM.
   */
  afterDomAttachment() {
    // NOP
  }

  private parseDataBinding(node: Node) {
    let ds = utils.findChild(node, 'DataSource');
    if (ds.attributes.getNamedItem('reference')) {
      ds = utils.getReferencedElement(ds);
    }

    let binding;
    const bindingClass = utils.parseStringAttribute(ds, 'class');
    if (bindingClass === 'ExternalDataSource') {
      binding = new ParameterBinding();
      const namesNode = utils.findChild(ds, 'Names');
      const entries = this.parseNames(namesNode);
      binding.opsName = entries.opsName;
      binding.pathName = entries.pathName;
      binding.sid = entries.sid;
      this.parameterBindings.push(binding);
    } else if (bindingClass === 'Computation') {
      binding = new ComputationBinding();
      binding.expression = utils.parseStringChild(ds, 'Expression');

      const argumentsNode = utils.findChild(ds, 'Arguments');
      for (const externalDataSourceNode of utils.findChildren(argumentsNode, 'ExternalDataSource')) {
        const arg = this.parseNames(utils.findChild(externalDataSourceNode, 'Names'));
        binding.args.push(arg);
      }
      binding.compileExpression();
      this.computationBindings.push(binding);
    } else {
      console.warn('Unexpected DataSource of type ' + bindingClass);
      return;
    }

    binding.usingRaw = utils.parseBooleanChild(ds, 'UsingRaw');
    binding.dynamicProperty = utils.parseStringChild(node, 'DynamicProperty');
  }

  private parseNames(node: Node) {
    let opsName;
    let pathName;
    let sid;
    for (const entryNode of utils.findChildren(node, 'entry')) {
      const stringNodes = utils.findChildren(entryNode, 'string');
      if (stringNodes.length === 2) {
        switch (stringNodes[0].textContent) {
          case 'Opsname':
            opsName = stringNodes[1].textContent || '';
            break;
          case 'Pathname':
            pathName = stringNodes[1].textContent || '';
            break;
          case 'SID':
            sid = stringNodes[1].textContent || '';
            break;
        }
      } else {
        console.warn(`Unexpected entry length ${stringNodes.length}`);
      }
    }

    return { opsName, pathName, sid };
  }

  updateBindings(parameterUpdate: ParameterUpdate) {
    for (const binding of this.parameterBindings) {
      if (binding.opsName === parameterUpdate.opsName) {
        const value = this.getParameterValue(parameterUpdate, binding.usingRaw);
        this.updateProperty(binding.dynamicProperty, value, parameterUpdate.acquisitionStatus, parameterUpdate.monitoringResult);
      }
    }
    for (const binding of this.computationBindings) {
      binding.updateDataSource(parameterUpdate.opsName, {
        value: this.getParameterValue(parameterUpdate, binding.usingRaw),
        acquisitionStatus: parameterUpdate.acquisitionStatus,
      });

      // We could do a bit better here by passing the acquisitionStatus etc through the expression engine, which
      // would allow for e.g. calculating the most severe acquisitionStatus for all inputs of a computation.
      // For now a pass-through of these attributes from the latest binding update seems sufficient.
      const value = binding.executeExpression();
      this.updateProperty(binding.dynamicProperty, value, parameterUpdate.acquisitionStatus, parameterUpdate.monitoringResult);

      // console.log(this.svg.getElementById(this.id));
      // console.log(this);
    }
  }

  protected abstract updateProperty(property: string, value: any, acquisitionStatus: string, monitoringResult: string): void;

  protected getFontMetrics(textString: string, fontFamily: string, textSize: number) {
    const el = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    el.setAttribute('font-family', fontFamily);
    el.setAttribute('font-size', String(textSize));
    el.appendChild(document.createTextNode(textString));
    this.display.measurerSvg.appendChild(el);
    const bbox = el.getBBox();
    this.display.measurerSvg.removeChild(el);
    return { height: bbox.height, width: bbox.width };
  }

  private getParameterValue(parameterUpdate: ParameterUpdate, usingRaw: boolean) {
    const val = (usingRaw ? parameterUpdate.rawValue : parameterUpdate.engValue);
    if (!val) {
      console.log('got parameter without value: ', parameterUpdate);
      return null;
    }

    switch (val.type) {
      case 'FLOAT':
        return val.floatValue;
      case 'DOUBLE':
        return val.doubleValue;
      case 'UINT32':
        return val.uint32Value;
      case 'SINT32':
        return val.sint32Value;
      case 'UINT64':
        return val.uint64Value;
      case 'SINT64':
        return val.sint64Value;
      case 'BOOLEAN':
        return val.booleanValue;
      case 'TIMESTAMP':
        return val.timestampValue;
      case 'BINARY':
        return window.atob(val.binaryValue);
      case 'STRING':
        return val.stringValue;
    }
  }

  protected generateChildId() {
    const id = `${this.id}c${this.childSequence}`;
    this.childSequence += 1;
    return id;
  }
}
