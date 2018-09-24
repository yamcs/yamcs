import { ParameterValue } from '@yamcs/client';
import { Tag } from '../../tags';
import { ComputationBinding } from '../ComputationBinding';
import { ComputationSample } from '../ComputationSample';
import { DataSourceBinding } from '../DataSourceBinding';
import { DataSourceSample } from '../DataSourceSample';
import { ParameterBinding } from '../ParameterBinding';
import { ParameterSample } from '../ParameterSample';
import { StyleSet } from '../StyleSet';
import { UssDisplay } from '../UssDisplay';
import * as utils from '../utils';

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

  // Marks if the models was updated since the last UI render
  dirty = false;

  svg: SVGSVGElement;
  childSequence = 0;

  tag: Tag;

  readonly styleSet: StyleSet;

  constructor(
    protected node: Element,
    protected display: UssDisplay) {

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

    this.styleSet = display.styleSet;
  }

  abstract parseAndDraw(): Tag;

  /**
   * Hook to perform logic after this widget (or any other widget) was added to the DOM.
   */
  afterDomAttachment() {
    // NOP
  }

  private parseDataBinding(node: Element) {
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
    this.registerBinding(binding);
  }

  protected abstract registerBinding(binding: DataSourceBinding): void;

  private parseNames(node: Element) {
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

  initializeBindings() {
    for (const binding of this.computationBindings) {
      if (binding.args.length === 0) {
        binding.sample = new ComputationSample(
          new Date(0),
          binding.executeExpression(),
          'COMPUTATION_OK',
          'UNKNOWN',
        );
        this.onBindingUpdate(binding, binding.sample);
      }
    }
  }

  updateBindings(sample: ParameterSample) {
    for (const binding of this.parameterBindings) {
      if (binding.opsName === sample.opsName) {
        binding.sample = sample;
        this.onBindingUpdate(binding, sample);
      }
    }
    for (const binding of this.computationBindings) {
      binding.updateDataSource(sample.opsName, {
        value: binding.usingRaw ? sample.rawValue : sample.engValue,
        acquisitionStatus: sample.acquisitionStatus,
      });

      // We could do a bit better here by passing the acquisitionStatus etc
      // through the expression engine, which would allow for calculating
      // the most severe acquisitionStatus for all inputs of a computation.
      // For now a pass-through of these attributes from the latest sample
      // seems sufficient.
      binding.sample = new ComputationSample(
        sample.generationTime,
        binding.executeExpression(),
        sample.acquisitionStatus,
        sample.monitoringResult,
      );
      this.onBindingUpdate(binding, binding.sample);
    }
  }

  digest() {
    // NOP
  }

  onDelivery(pvals: ParameterValue[]) {
  }

  protected onBindingUpdate(binding: DataSourceBinding, sample: DataSourceSample) {
  }

  protected getFontMetrics(text: string, fontFamily: string, fontStyle: string, fontWeight: string, fontSize: string) {
    const el = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    el.setAttribute('font-family', fontFamily);
    el.setAttribute('font-style', fontStyle);
    el.setAttribute('font-weight', fontWeight);
    el.setAttribute('font-size', fontSize);
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
