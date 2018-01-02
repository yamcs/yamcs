import * as utils from '../utils';

import { AbstractWidget } from './AbstractWidget';
import { Parameter } from '../Parameter';

export class Symbol extends AbstractWidget {

  parseAndDraw(svg: any, parent: any, e: Node) {
    // svg.image(parent, opts.x, opts.y, opts.width, opts.height, "U19_led_3D_grey.svg");
    const libraryName = utils.parseStringChild(e, 'LibraryName');
    const symbolName = utils.parseStringChild(e, 'SymbolName');

    const settings = { id: this.id, class: 'uss-symbol' };
    const img = svg.image(parent, this.x, this.y, this.width, this.height, '', settings);
    this.libraryName = libraryName;
    this.symbolName = symbolName;

    let sl = this.symbolLibrary[libraryName];
    if (sl === undefined) {
        this.loadSymbolLibrary(libraryName);
    }
    // TODO this should be async after the library has been loaded
    sl = this.symbolLibrary[libraryName];
    if (sl && sl.loaded) {
        const s = sl[symbolName];
        if (s === undefined) {
            console.log('Cannot find symbol ' + symbolName + ' in library ' + libraryName);
        } else {
            this.symbol = s;
            img.setAttribute('href', '/_static/symlib/images/' + s.defaultImage);
        }
    }
  }

  loadSymbolLibrary(libraryName: string) {
      // console.log("loading symbol library ", libraryName);
      const sl = {};
      sl.loaded = false;
      this.symbolLibrary[libraryName] = sl;
      $.ajax({
          url: `/_static/symlib/${libraryName}.xml`,
          async: false
      }).done(function(xmlData: any) {
          $('library symbol', xmlData).each(function(idx: any, val: any) {
              const s = new Object();
              s.type = $(val).children('type').text();
              s.name = $(val).children('name').text();
              s.states = {};
              $('image', val).each(function(idx1: any, val1: any) {
                  const state = val1.getAttribute('state');
                  const img = $(val1).text();
                  if (state) {
                      s.states[state] = img;
                  }
                  if (s.type === 'dynamic') {
                      const def = val1.getAttribute('default').toLowerCase() === 'true';
                      if (def) {
                          s.defaultImage = img;
                      }
                  } else {
                       s.defaultImage = img;
                  }
              });
              sl[s.name] = s;
          });
          sl.loaded = true;
       });
  }

  updateValue(para: Parameter, usingRaw: boolean) {
    const value = this.getParameterValue(para, usingRaw);
    let img = this.symbol.states[value];
    if (img === undefined) {
      img = symbol.defaultImage;
    }
    const svgimg = this.svg.getElementById(this.id);
    if (svgimg) {
      svgimg.setAttribute('href', '/_static/symlib/images/' + img);
    }
  }
}
