import { Tag } from '../tags';
import * as utils from '../utils';
import { Display } from '../Display';
import { ParameterUpdate } from '../ParameterUpdate';
import { ParameterBinding, ARG_OPSNAME, ARG_PATHNAME, ARG_SID } from '../ParameterBinding';
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
      const entries = this.parseEntries(namesNode);
      if (ARG_OPSNAME in entries) {
        binding.opsName = entries[ARG_OPSNAME];
      } else {
        console.warn('External Data source without Opsname', ds);
        return;
      }
      if (ARG_PATHNAME in entries) {
        binding.pathName = entries[ARG_PATHNAME];
      }
      if (ARG_SID in entries) {
        binding.sid = entries[ARG_SID];
      }
      this.parameterBindings.push(binding);
    } else if (bindingClass === 'Computation') {
      binding = new ComputationBinding();
      binding.expression = utils.parseStringChild(ds, 'Expression');

      const argumentsNode = utils.findChild(ds, 'Arguments');
      for (const externalDataSourceNode of utils.findChildren(argumentsNode, 'ExternalDataSource')) {
        binding.args = this.parseEntries(utils.findChild(externalDataSourceNode, 'Names'));
      }

      const namesNode = utils.findChild(ds, 'Names');
      const entries = this.parseEntries(namesNode);
      if ('DEFAULT' in entries) {
        binding.name = entries['DEFAULT'];
      }
      this.computationBindings.push(binding);
    } else {
      console.warn('Unexpected DataSource of type ' + bindingClass);
      return;
    }

    binding.usingRaw = utils.parseBooleanChild(ds, 'UsingRaw');
    binding.dynamicProperty = utils.parseStringChild(node, 'DynamicProperty');
  }

  /**
   * Parses a structure like this:
   *
   * <entry>
   *  <string>Opsname</string>
   *  <string>Emergency_Stop_Run_Time_BIT</string>
   * </entry>
   * <entry>
   *  <string>Pathname</string>
   *  <string>\\Emergency_Stop_Run_Time_BIT</string>
   * </entry>
   * <entry>
   *  <string>SID</string>
   *  <string>1453</string>
   * </entry>
   */
  private parseEntries(node: Node) {
    const pairs: { [key: string]: string } = {};
    for (const entryNode of utils.findChildren(node, 'entry')) {
      const stringNodes = utils.findChildren(entryNode, 'string');
      if (stringNodes.length === 2) {
        const entryType = stringNodes[0].textContent || '';
        const entryValue = stringNodes[1].textContent || '';
        pairs[entryType] = entryValue;
      } else {
        console.warn(`Unexpected entry length ${stringNodes.length}`);
      }
    }
    return pairs;
  }

  updateBindings(parameterUpdate: ParameterUpdate) {
    for (const binding of this.parameterBindings) {
      if (binding.opsName === parameterUpdate.opsName) {
        switch (binding.dynamicProperty) {
          case 'VALUE':
            this.updateValue(parameterUpdate, binding.usingRaw);
            break;
          case 'X':
            this.updatePosition(parameterUpdate, 'x', binding.usingRaw);
            break;
          case 'Y':
            this.updatePosition(parameterUpdate, 'y', binding.usingRaw);
            break;
          case 'FILL_COLOR':
            this.updateFillColor(parameterUpdate, binding.usingRaw);
            break;
          default:
            console.warn('Unsupported dynamic property: ' + binding.dynamicProperty);
        }
      }
    }
  }

  protected updateValue(parameterUpdate: ParameterUpdate, usingRaw: boolean) {
    console.log('updateValue called on AbstractWidget', this);
  }

  protected updatePosition(parameterUpdate: ParameterUpdate, attribute: 'x' | 'y', usingRaw: boolean) {
    console.log('updatePosition called on AbstractWidget', this);
  }

  protected updateFillColor(parameterUpdate: ParameterUpdate, usingRaw: boolean) {
    console.log('updateFillColor called on AbstractWidget', this);
  }

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

  protected getParameterValue(parameterUpdate: ParameterUpdate, usingRaw: boolean) {
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
