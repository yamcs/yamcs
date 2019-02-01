import { NamedObjectId, ParameterValue } from '@yamcs/client';
import { Display } from '../Display';
import { DisplayCommunicator } from '../DisplayCommunicator';
import { NavigationHandler } from '../NavigationHandler';
import { Defs, Pattern, Rect, Svg, Tag } from '../tags';
import { Color } from './Color';
import * as constants from './constants';
import { CompiledFormula } from './formulas/CompiledFormula';
import { FormulaCompiler } from './formulas/FormulaCompiler';
import * as utils from './utils';
import { AbstractWidget } from './widgets/AbstractWidget';
import { ActionButton } from './widgets/ActionButton';
import { Arc } from './widgets/Arc';
import { BooleanButton } from './widgets/BooleanButton';
import { BooleanSwitch } from './widgets/BooleanSwitch';
import { Connection } from './widgets/Connection';
import { Ellipse } from './widgets/Ellipse';
import { GroupingContainer } from './widgets/GroupingContainer';
import { Image } from './widgets/Image';
import { Label } from './widgets/Label';
import { LED } from './widgets/LED';
import { LinkingContainer } from './widgets/LinkingContainer';
import { Polygon } from './widgets/Polygon';
import { Polyline } from './widgets/Polyline';
import { Rectangle } from './widgets/Rectangle';
import { RoundedRectangle } from './widgets/RoundedRectangle';
import { TabbedContainer } from './widgets/TabbedContainer';
import { TextInput } from './widgets/TextInput';
import { TextUpdate } from './widgets/TextUpdate';

export class OpiDisplay implements Display {

  private widgets: AbstractWidget[] = [];
  private qualifiedNames = new Set<string>();
  private widgetsByTrigger = new Map<string, AbstractWidget[]>();

  private formulas = new Map<string, CompiledFormula>();
  private formulasByTrigger = new Map<string, CompiledFormula[]>();

  title: string;
  width: number;
  height: number;
  bgcolor: Color;

  defs: Defs;

  container: HTMLDivElement;
  measurerSvg: SVGSVGElement;

  constructor(
    private id: string,
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

  async parseAndDraw(id: string, grid = true) {
    // Preload the Liberation font so that font metrics are correctly calculated.
    // Probably can be done without external library in about 5 years from now.
    // Follow browser support of this spec: https://www.w3.org/TR/css-font-loading-3/
    const fontFace = 'Liberation Sans';
    try { // Times out after 3 seconds
      await Promise.all([
        new FontFaceObserver(fontFace, { weight: 'normal', style: 'normal' }).load(),
        new FontFaceObserver(fontFace, { weight: 'normal', style: 'italic' }).load(),
        new FontFaceObserver(fontFace, { weight: 'bold', style: 'normal' }).load(),
        new FontFaceObserver(fontFace, { weight: 'bold', style: 'italic' }).load(),
      ]);
    } catch {
      // tslint:disable-next-line:no-console
      console.warn(`Failed to load all font variants for '${fontFace}'. Font metric calculations may not be accurate.`);
    }

    return this.displayCommunicator.getXMLObject('displays', this.id).then(doc => {
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

  public resolve(path: string): string {
    let currentFolder = '';
    const idx = this.id.lastIndexOf('/');
    if (idx !== -1) {
      currentFolder = this.id.substring(0, idx);
    }
    return utils.normalizePath(currentFolder, path);
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
      case constants.TYPE_ACTION_BUTTON:
        return new ActionButton(node, this);
      case constants.TYPE_ARC:
        return new Arc(node, this);
      case constants.TYPE_BOOLEAN_BUTTON:
        return new BooleanButton(node, this);
      case constants.TYPE_BOOLEAN_SWITCH:
        return new BooleanSwitch(node, this);
      case constants.TYPE_ELLIPSE:
        return new Ellipse(node, this);
      case constants.TYPE_GROUPING_CONTAINER:
        return new GroupingContainer(node, this);
      case constants.TYPE_IMAGE:
        return new Image(node, this);
      case constants.TYPE_LABEL:
        return new Label(node, this);
      case constants.TYPE_LED:
        return new LED(node, this);
      case constants.TYPE_LINKING_CONTAINER:
        return new LinkingContainer(node, this);
      case constants.TYPE_POLYGON:
        return new Polygon(node, this);
      case constants.TYPE_POLYLINE:
        return new Polyline(node, this);
      case constants.TYPE_RECTANGLE:
        return new Rectangle(node, this);
      case constants.TYPE_ROUNDED_RECTANGLE:
        return new RoundedRectangle(node, this);
      case constants.TYPE_TABBED_CONTAINER:
        return new TabbedContainer(node, this);
      case constants.TYPE_TEXT_INPUT:
        return new TextInput(node, this);
      case constants.TYPE_TEXT_UPDATE:
        return new TextUpdate(node, this);
      default:
        // tslint:disable-next-line:no-console
        console.warn(`Unsupported widget type: ${typeId}`);
    }
  }

  addWidget(widget: AbstractWidget, parent: Tag) {
    parent.addChild(widget.tag);
    this.widgets.push(widget);

    if (widget.pvName) {
      if (widget.pvName.startsWith('=')) {
        let compiledFormula = this.formulas.get(widget.pvName);
        if (!compiledFormula) {
          const compiler = new FormulaCompiler();
          compiledFormula = compiler.compile(widget.pvName);
        }

        // Incoming values first trigger a (possibly shared) formula.
        // These formulas then trigger the widget.
        this.registerWidgetTriggers(widget.pvName, widget);
        for (const parameter of compiledFormula.getParameters()) {
          this.registerFormulaTriggers(parameter, compiledFormula);
          this.qualifiedNames.add(parameter);
        }
      } else if (widget.pvName.startsWith('sim://')) {
        console.warn(`Ignoring PV ${widget.pvName}`);
      } else if (widget.pvName.startsWith('loc://')) {
        console.warn(`Ignoring PV ${widget.pvName}`);
      } else if (widget.pvName.startsWith('sys://')) {
        console.warn(`Ignoring PV ${widget.pvName}`);
      } else {
        this.registerWidgetTriggers(widget.pvName, widget);
        this.qualifiedNames.add(widget.pvName);
      }
    }
  }

  private registerWidgetTriggers(pvName: string, widget: AbstractWidget) {
    const widgets = this.widgetsByTrigger.get(pvName);
    if (widgets) {
      widgets.push(widget);
    } else {
      this.widgetsByTrigger.set(pvName, [widget]);
    }
  }

  private registerFormulaTriggers(qualifiedName: string, formula: CompiledFormula) {
    const formulas = this.formulasByTrigger.get(qualifiedName);
    if (formulas) {
      formulas.push(formula);
    } else {
      this.formulasByTrigger.set(qualifiedName, [formula]);
    }
  }

  getParameterIds() {
    const ids: NamedObjectId[] = [];
    this.qualifiedNames.forEach(name => ids.push({ name }));
    console.log('Do sub with', ids);
    return ids;
  }

  getDataSourceState() {
    const green = false;
    const yellow = false;
    const red = false;
    return { green, yellow, red };
  }

  processParameterValues(pvals: ParameterValue[]) {
    for (const widget of this.widgets) {
      widget.onDelivery(pvals);
    }

    for (const pval of pvals) {
      const formulas = this.formulasByTrigger.get(pval.id.name);
      if (formulas) {
        const dirtyFormulas = [];
        for (const formula of formulas) {
          formula.updateDataSource(pval.id.name, {
            value: utils.unwrapParameterValue(pval.engValue),
            acquisitionStatus: null,
          });
          dirtyFormulas.push(formula);
        }

        for (const formula of dirtyFormulas) {
          const value = formula.execute();
          this.markDirtyWidgets(formula.pvName, value);
        }
      }
    }
    for (const pval of pvals) {
      const value = utils.unwrapParameterValue(pval.engValue);
      this.markDirtyWidgets(pval.id.name, value);
    }
  }

  private markDirtyWidgets(pvName: string, value: any) {
    const widgets = this.widgetsByTrigger.get(pvName);
    if (widgets) {
      for (const widget of widgets) {
        widget.onPV({ name: pvName, value });
        widget.dirty = true;
      }
    }
  }

  digest() {
    for (const widget of this.widgets) {
      if (widget.dirty) {
        widget.digest();
        widget.dirty = false;
      }
    }
  }

  findWidget(wuid: string) {
    for (const widget of this.widgets) {
      if (widget.wuid === wuid) {
        return widget;
      }
    }
  }
}
