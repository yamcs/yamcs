import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Image } from '../tags';
import { ResourceResolver } from '../ResourceResolver';

export class Symbol extends AbstractWidget {

  libraryName: string;
  symbolName: string;
  symbol: SymbolDefinition;

  libraries: { [key: string]: SymbolLibrary };
  resolver: ResourceResolver;

  // DOM
  symbolEl: Element;

  parseAndDraw() {
    this.libraries = {};
    this.libraryName = utils.parseStringChild(this.node, 'LibraryName');
    this.symbolName = utils.parseStringChild(this.node, 'SymbolName');
    this.resolver = this.display.resourceResolver;

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

    return this.resolver.resolve(`symlib/${libraryName}.xml`).then(xmlString => {
      const xmlParser = new DOMParser();
      const doc = xmlParser.parseFromString(xmlString, 'text/xml');
      lib.parse(doc as XMLDocument);
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
          const href = this.resolver.resolvePath(`symlib/images/${symbol.defaultImage}`);
          this.symbolEl.setAttribute('href', href);
        }
      });
    }
  }

  updateProperty(property: string, value: any, acquisitionStatus: string, monitoringResult: string) {
    switch (property) {
      case 'VALUE':
        const file = this.symbol.states[value] || this.symbol.defaultImage;
        this.symbolEl.setAttribute('href', this.resolver.resolvePath(`symlib/images/${file}`));
        break;
      default:
        console.warn('Unsupported dynamic property: ' + property);
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
    const res = [];
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
