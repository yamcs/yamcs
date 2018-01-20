import { Parameter } from '../Parameter';
import { Tag } from '../tags';
import * as utils from '../utils';
import { DataBinding, ARG_OPSNAME, ARG_PATHNAME, ARG_SID } from '../DataBinding';
import { Display } from '../Display';

export abstract class AbstractWidget {

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
    public sequenceNumber: number,
    protected node: Node,
    protected display: Display) {

    this.id = `w${sequenceNumber}`;
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

    this.tag = this.parseAndDraw();
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
        const entryValue = stringNodes[0].textContent || '';
        pairs[entryType] = entryValue;
      } else {
        console.warn(`Unexpected entry length ${stringNodes.length}`);
      }
    }
    return pairs;
  }

  updateValue(para: Parameter, usingRaw: boolean) {
    console.log('updateValue called on AbstractWidget', this);
  }

  updatePosition(para: Parameter, attribute: 'x' | 'y', usingRaw: boolean) {
    const e = this.svg.getElementById(this.id);
    const newpos = this.getParameterValue(para, usingRaw);
    e.setAttribute(attribute, newpos);
  }

  protected updatePositionByTranslation(svgid: string, para: Parameter, attribute: 'x' | 'y', usingRaw: boolean) {
    const e = this.svg.getElementById(svgid);
    const newpos = this.getParameterValue(para, usingRaw);
    if (attribute === 'x') {
      this.x = newpos;
    } else if (attribute === 'y') {
      this.y = newpos;
    }
    e.setAttribute('transform', `translate(${this.x},${this.y})`);
  }

  updateFillColor(para: Parameter, usingRaw: boolean) {
    const el = this.svg.getElementById(this.id);
    el.setAttribute('stroke', this.getParameterValue(para, usingRaw));
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

  protected getParameterValue(param: Parameter, usingRaw: boolean) {
    if (usingRaw) {
      const rv = param.rawValue;
      for (const idx of rv) {
        if (idx !== 'type') {
          return rv[idx];
        }
      }
    } else {
      const ev = param.engValue;
      if (ev === undefined) {
        console.log('got parameter without engValue: ', param);
        return null;
      }
      switch (ev.type) {
        case 'FLOAT':
          return ev.floatValue;
        case 'DOUBLE':
          return ev.doubleValue;
        case 'BINARY':
          return window.atob(ev.binaryValue);
      }
      for (const idx of ev) {
        if (idx !== 'type') {
          return ev[idx];
        }
      }
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
