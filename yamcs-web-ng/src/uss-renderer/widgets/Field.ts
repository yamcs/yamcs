import * as utils from '../utils';

const sprintf = require('sprintf-js').sprintf;


import { AbstractWidget } from './AbstractWidget';
import { Parameter } from '../Parameter';

export class Field extends AbstractWidget {

  decimals: number;
  format: string;

  // the values are CSS classes defined in uss.dqi.css
  alldqi: [
    'dead',
    'disabled',
    'in_limits',
    'nominal_limit_violation',
    'danger_limit_violation',
    'static',
    'undefined'
  ];

  overrideDqi: boolean;

  parseAndDraw(svg: any, parent: any, e: Node) {
    // make a group to put the text and the bounding box together
    let settings: {[key: string]: any} = {
      transform: `translate(${this.x},${this.y})`,
      class: 'context-menu-field'
    };

    parent = svg.group(parent, this.id + '-group', settings);
    parent.ussWidget = this;

    this.decimals = utils.parseIntChild(e, 'Decimals', 0);
    this.format = utils.parseStringChild(e, 'Format');
    this.overrideDqi = utils.parseBooleanChild(e, 'OverrideDQI', false);

    let unitWidth = 0;
    const unit = utils.parseStringChild(e, 'Unit');
    if (unit && utils.parseBooleanChild(e, 'ShowUnit')) {
      const unitTextStyleNode = utils.findChild(e, 'UnitTextStyle');
      const unitTextStyle = utils.parseTextStyle(unitTextStyleNode);
      const ut = svg.text(parent, 0, 0, unit, unitTextStyle);

      const bbox = ut.getBBox();
      ut.setAttribute('dx', this.width - bbox.width);


      const unitVertAlignment = utils.parseStringChild(unitTextStyleNode, 'VerticalAlignment').toLowerCase();
      if (unitVertAlignment === 'center') {
        ut.setAttribute('dy',  -bbox.y + (this.height - bbox.height) / 2);
      } else if (unitVertAlignment === 'top') {
        ut.setAttribute('dy',  -bbox.y);
      } else if (unitVertAlignment === 'bottom') {
        ut.setAttribute('dy', -bbox.y + (this.height - bbox.height));
      }
      unitWidth = bbox.width + 2;
    }
    this.width -= unitWidth;
    settings = { id: this.id + '-background' };
    if (!this.overrideDqi) {
      settings.class = 'dead-background';
    }

    const id = this.getWidgetParameter();
    if (id) {
      const yamcsInstance = location.pathname.match(/\/([^\/]*)\/?/)[1];
      const rectLink = svg.link(parent, '/' + yamcsInstance + '/mdb/' + id.namespace + '/' + id.name, {});
      svg.rect(rectLink, 0, 0, this.width, this.height, settings);
    } else {
      svg.rect(parent, 0, 0, this.width, this.height, settings);
    }

    utils.writeText(svg, parent, {id: this.id, x: 0, y: 0, width: this.width, height: this.height}, utils.findChild(e, 'TextStyle'), ' ');
  }

  updateValue(para: Parameter) {
    let v = this.getParameterValue(para, this.usingRaw);
    if (typeof v === 'number') {
      if (this.format) {
        v = sprintf(this.format, v);
      } else {
        v = v.toFixed(this.decimals);
      }
    }
    const svg = this.svg;
    const ftxt = svg.getElementById(this.id);
    if (!ftxt) {
      return; // TODO temp until we unregister bindings upon window close
    }
    ftxt.textContent = v;
    if (!this.overrideDqi) {
      const dqi = this.getDqi(para);
      svg.configure(ftxt, {class: dqi + '-foreground'});
      const fbg = svg.getElementById(this.id + '-background');
      svg.configure(fbg, {class: dqi + '-background'});
    }
  }

  updatePosition(para: Parameter, attribute: 'x' | 'y', usingRaw: boolean) {
    this.updatePositionByTranslation(this.id + '-group', para, attribute, usingRaw);
  }

  updateFillColor(para: Parameter, usingRaw: boolean) {
    if (!this.overrideDqi) {
      return;
    }
    const newcolor = this.getParameterValue(para, usingRaw);
    const svg = this.svg;
    const fbg = svg.getElementById(this.id + '-background');
    svg.configure(fbg, {fill: newcolor});
  }

  // implements based on the mcs_dqistyle.xml
  getDqi(para: any) {
    switch (para.acquisitionStatus) {
      case 'ACQUIRED':
        switch (para.monitoringResult) {
          case 'DISABLED':
            return 'disabled';
          case 'IN_LIMITS':
            return 'in_limits';
          case 'WATCH':
          case 'WARNING':
          case 'DISTRESS':
            return 'nominal_limit_violation';
          case 'CRITICAL':
          case 'SEVERE':
            return 'danger_limit_violation';
          case undefined:
            return 'undefined';
        }
        break;
      case 'NOT_RECEIVED':
        return 'dead';
      case 'INVALID':
        return 'dead';
      case 'EXPIRED':
        return 'expired';
    }
  }
}
