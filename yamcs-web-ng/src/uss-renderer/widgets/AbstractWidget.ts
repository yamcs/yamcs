import { Tag } from '../tags';
import * as utils from '../utils';
import { DataBinding, ARG_OPSNAME, ARG_PATHNAME, ARG_SID } from '../DataBinding';
import { Display } from '../Display';
import { ParameterUpdate } from '../ParameterUpdate';

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
  dataBindings: DataBinding[];

  svg: SVGSVGElement;
  childSequence = 0;
  computationSequence = 0;

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

    this.dataBindings = [];
    const dataBindingsNode = utils.findChild(node, 'DataBindings');
    for (const childNode of utils.findChildren(dataBindingsNode, 'DataBinding')) {
      const dataBinding = this.parseDataBinding(childNode);
      if (dataBinding) {
        this.dataBindings.push(dataBinding);
      }
    }
  }

  abstract parseAndDraw(): Tag;

  /**
   * Hook to perform logic after this widget (or any other widget) was added to the DOM.
   */
  afterDomAttachment() {
    // NOP
  }

  private parseDataBinding(e: Node) {
    const db = new DataBinding();
    db.dynamicProperty = utils.parseStringChild(e, 'DynamicProperty');
    let ds = utils.findChild(e, 'DataSource');
    if (ds.attributes.getNamedItem('reference')) {
      ds = utils.getReferencedElement(ds);
    }
    db.type = utils.parseStringAttribute(ds, 'class');
    if (db.type === 'ExternalDataSource') {
      const namesNode = utils.findChild(ds, 'Names');
      const entries = this.parseEntries(namesNode);
      if (ARG_OPSNAME in entries) {
        db.opsname = entries[ARG_OPSNAME];
      } else {
        console.warn('External Data source without Opsname', ds);
        return;
      }
      if (ARG_PATHNAME in entries) {
        db.pathname = entries[ARG_PATHNAME];
      }
      if (ARG_SID in entries) {
        db.sid = entries[ARG_SID];
      }
      db.usingRaw = utils.parseBooleanChild(ds, 'UsingRaw');
    } else if (db.type === 'Computation') {
      db.opsname = this.generateComputationId();
      db.expression = utils.parseStringChild(ds, 'Expression');

      const argumentsNode = utils.findChild(ds, 'Arguments');
      for (const externalDataSourceNode of utils.findChildren(argumentsNode, 'ExternalDataSource')) {
        db.args = this.parseEntries(utils.findChild(externalDataSourceNode, 'Names'));
      }

      const namesNode = utils.findChild(ds, 'Names');
      const entries = this.parseEntries(namesNode);
      if ('DEFAULT' in entries) {
        db.DEFAULT = entries['DEFAULT'];
      }
    }
    return db;
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

  updateValue(parameterUpdate: ParameterUpdate, usingRaw: boolean) {
    console.log('updateValue called on AbstractWidget', this);
  }

  updatePosition(parameterUpdate: ParameterUpdate, attribute: 'x' | 'y', usingRaw: boolean) {
    const e = this.svg.getElementById(this.id);
    const newpos = this.getParameterValue(parameterUpdate, usingRaw);
    e.setAttribute(attribute, newpos);
  }

  protected updatePositionByTranslation(svgid: string, parameterUpdate: ParameterUpdate, attribute: 'x' | 'y', usingRaw: boolean) {
    const e = this.svg.getElementById(svgid);
    const newpos = this.getParameterValue(parameterUpdate, usingRaw);
    if (attribute === 'x') {
      this.x = newpos;
    } else if (attribute === 'y') {
      this.y = newpos;
    }
    e.setAttribute('transform', `translate(${this.x},${this.y})`);
  }

  updateFillColor(parameterUpdate: ParameterUpdate, usingRaw: boolean) {
    const el = this.svg.getElementById(this.id);
    el.setAttribute('stroke', this.getParameterValue(parameterUpdate, usingRaw));
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

  protected getWidgetParameter() {
    for (const dataBinding of this.dataBindings) {
      if (dataBinding.dynamicProperty === 'VALUE') {
        return dataBinding.opsname;
      }
    }
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

  protected generateComputationId() {
    const id = `__uss_computation_${this.id}c${this.computationSequence}`;
    this.computationSequence += 1;
    return id;
  }
}
