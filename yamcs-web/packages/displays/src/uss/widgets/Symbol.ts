import { DisplayCommunicator } from '../../DisplayCommunicator';
import { Image } from '../../tags';
import { DataSourceBinding } from '../DataSourceBinding';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';


export class Symbol extends AbstractWidget {

  libraryName: string;
  symbolName: string;
  private symbol: SymbolDefinition;

  private libraries: { [key: string]: SymbolLibrary };
  displayCommunicator: DisplayCommunicator;

  valueBinding: DataSourceBinding;

  // DOM
  symbolEl: Element;

  parseAndDraw() {
    this.libraries = {};
    this.libraryName = utils.parseStringChild(this.node, 'LibraryName');
    this.symbolName = utils.parseStringChild(this.node, 'SymbolName');
    this.displayCommunicator = this.display.displayCommunicator;

    return new Image({
      id: this.id,
      class: 'symbol',
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      'data-name': this.name,
    });
  }

  loadSymbolLibrary(libraryName: string) {
    const lib = new SymbolLibrary();
    this.libraries[libraryName] = lib;

    return this.displayCommunicator.getXMLObject('uss', `symlib/${libraryName}.xml`).then(doc => {
      lib.parse(doc);
    });
  }

  afterDomAttachment() {
    this.symbolEl = this.svg.getElementById(this.id);

    let lib = this.libraries[this.libraryName];
    if (!lib) {
      this.loadSymbolLibrary(this.libraryName).then(() => {
        lib = this.libraries[this.libraryName];
        const symbol = lib.symbols[this.symbolName];
        if (!symbol) {
          console.warn(`Cannot find symbol ${this.symbolName} in library ${this.libraryName}`);
        } else {
          this.symbol = symbol;
          const href = this.displayCommunicator.getObjectURL('uss', `symlib/images/${symbol.defaultImage}`);
          this.symbolEl.setAttribute('href', href);
        }
      });
    }
  }

  registerBinding(binding: DataSourceBinding) {
    switch (binding.dynamicProperty) {
      case 'VALUE':
        this.valueBinding = binding;
        break;
      default:
        console.warn('Unsupported dynamic property: ' + binding.dynamicProperty);
    }
  }

  digest() {
    if (this.valueBinding && this.valueBinding.sample) {
      const value = this.valueBinding.value;
      const file = this.symbol.states[value] || this.symbol.defaultImage;
      this.symbolEl.setAttribute('href', this.displayCommunicator.getObjectURL('uss', `symlib/images/${file}`));
    }
  }
}

class SymbolLibrary {
  loaded = false;
  symbols: { [key: string]: SymbolDefinition } = {};

  parse(doc: XMLDocument) {
    const libraryNode = utils.findChild(doc, 'library');
    for (const symbolNode of utils.findChildren(libraryNode, 'symbol')) {
      const s = new SymbolDefinition();
      s.type = utils.parseStringChild(symbolNode, 'type');
      s.name = utils.parseStringChild(symbolNode, 'name');
      s.states = {};
      for (const imageNode of utils.findChildren(symbolNode, 'image')) {
        const file = imageNode.textContent;
        if (!file) {
          continue;
        }

        if (s.type === 'dynamic') {
          const state = utils.parseStringAttribute(imageNode, 'state');
          if (state) {
            s.states[state] = file;
          }
          if (utils.parseBooleanAttribute(imageNode, 'default')) {
            s.defaultImage = file;
          }
        } else {
          s.defaultImage = file;
        }
      }
      this.symbols[s.name] = s;
    }
    this.loaded = true;
  }

  toString() {
    const res: SymbolDefinition[] = [];
    for (const key of Object.keys(this.symbols)) {
      res.push(this.symbols[key]);
    }
    return res.join('\n');
  }
}

class SymbolDefinition {
  type: string;
  name: string;
  states: { [key: string]: string } = {};
  defaultImage: string;

  toString() {
    return `${this.name}: ${this.defaultImage} (${this.type})`;
  }
}
