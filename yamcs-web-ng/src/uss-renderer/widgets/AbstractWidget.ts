import * as utils from '../utils';
import { DataBinding } from '../DataBinding';
import { Parameter } from '../Parameter';

export abstract class AbstractWidget {

  id: string;
  x: number;
  y: number;
  width: number;
  height: number;

  dataBindings: DataBinding[];

  svg: any;

  abstract parseAndDraw(svg: any, parent: any, e: Node): void;

  updateValue(svg: any, para: Parameter, usingRaw: boolean) {
    console.log('updateValue called on AbstractWidget. this:', this);
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
    e.setAttribute('transform', 'translate(' + this.x + ',' + this.y + ')');
  }

  updateFillColor(para: Parameter, usingRaw: boolean) {
    const e = this.svg.getElementById(this.id);
    const newcolor = this.getParameterValue(para, usingRaw);
    this.svg.configure(e, {stroke: newcolor});
  }

  protected getWidgetParameter() {
    for (const dataBinding of this.dataBindings) {
      if (dataBinding.dynamicProperty === 'VALUE') {
        return {
          namespace: dataBinding.parameterNamespace,
          name: dataBinding.parameterName,
        };
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
}
