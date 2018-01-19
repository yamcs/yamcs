import * as utils from '../utils';

const sprintf = require('sprintf-js').sprintf;


import { AbstractWidget } from './AbstractWidget';
import { Parameter } from '../Parameter';
import { G, Rect, Text, ClipPath } from '../tags';

export class Field extends AbstractWidget {

  decimals: number;
  format: string | null;

  overrideDqi: boolean;

  parseAndDraw() {
    // make a group to put the text and the bounding box together
    const g = new G({
      id: `${this.id}-group`,
      transform: `translate(${this.x},${this.y})`,
      class: 'field',
      'data-name': this.name,
    });

    this.decimals = utils.parseIntChild(this.node, 'Decimals', 0);
    if (utils.hasChild(this.node, 'Format')) {
      this.format = utils.parseStringChild(this.node, 'Format');
    }
    this.overrideDqi = utils.parseBooleanChild(this.node, 'OverrideDQI', false);

    if (utils.hasChild(this.node, 'Unit') && utils.parseBooleanChild(this.node, 'ShowUnit')) {
      const unitWidth = 0;
      const unit = utils.parseStringChild(this.node, 'Unit');
      const unitTextStyleNode = utils.findChild(this.node, 'UnitTextStyle');
      const unitTextStyle = utils.parseTextStyle(unitTextStyleNode);
      const ut = new Text({
        x: 0,
        y: 0,
        value: unit,
        ...unitTextStyle,
      });
      g.addChild(ut);

      /* TODO
      const bbox = ut.getBBox();
      ut.setAttribute('dx', this.width - bbox.width);

      const unitVertAlignment = utils.parseStringChild(unitTextStyleNode, 'VerticalAlignment').toLowerCase();
      if (unitVertAlignment === 'center') {
        ut.setAttribute('dy', -bbox.y + (this.height - bbox.height) / 2);
      } else if (unitVertAlignment === 'top') {
        ut.setAttribute('dy', -bbox.y);
      } else if (unitVertAlignment === 'bottom') {
        ut.setAttribute('dy', -bbox.y + (this.height - bbox.height));
      }
      unitWidth = bbox.width + 2;
      */
      this.width -= unitWidth;
    }

    // Don't know why, but box widths in USS appear to grow per 6 pixels only
    const boxWidth = this.width - (this.width % 6);

    const rect = new Rect({
      id: `${this.id}-bg`,
      x: 0,
      y: 0,
      width: boxWidth,
      height: this.height,
      ...utils.parseFillStyle(this.node),
    });

    const opsname = this.getWidgetParameter();
    if (opsname) {
      const yamcsInstance = 'dev'; // TODO window.location.pathname.match(/\/([^\/]*)\/?/)[1];
      rect.setAttribute('xlink:href', `/${yamcsInstance}/mdb/MDB:OPS Name/${opsname}`);
    }
    if (!this.overrideDqi) {
      rect.setAttribute('class', 'dead-bg');
    }
    g.addChild(rect);

    const textStyleNode = utils.findChild(this.node, 'TextStyle');

    // Clip text within the defined boundary.
    // TODO clip-path (nor -webkit-clip-path) does not work on Safari
    const clipId = this.generateChildId();
    g.addChild(new ClipPath({ id: clipId }).addChild(
      new Rect({
        x: 0,
        y: 0,
        width: boxWidth,
        height: this.height,
      })
    ));

    const text = new Text({
      id: this.id,
      y: 0,
      ...utils.parseTextStyle(textStyleNode),
      'clip-path': `url(#${clipId})`,
    });
    g.addChild(text);

    let x;
    const horizAlignment = utils.parseStringChild(textStyleNode, 'HorizontalAlignment');
    if (horizAlignment === 'CENTER') {
      x = 0 + boxWidth / 2;
      text.setAttribute('text-anchor', 'middle');
    } else if (horizAlignment === 'LEFT') {
      x = 0;
      text.setAttribute('text-anchor', 'start');
    } else if (horizAlignment === 'RIGHT') {
      x = 0 + boxWidth;
      text.setAttribute('text-anchor', 'end');
    }
    text.setAttribute('x', String(x));

    // TODO move to update
    // Prefer FontMetrics over baseline tricks to account for
    // ascends and descends.
    const fontSize = Number(text.attributes['font-size']);
    const fm = this.getFontMetrics(/*innerText*/ '', fontSize);

    const vertAlignment = utils.parseStringChild(textStyleNode, 'VerticalAlignment');
    if (vertAlignment === 'CENTER') {
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('y', String(0 + (this.height / 2)));
    } else if (vertAlignment === 'TOP') {
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('y', String(0 + (fm.height / 2)));
    } else if (vertAlignment === 'BOTTOM') {
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('y', String(0 + + this.height - (fm.height / 2)));
    }

    return g;
  }

  updateValue(para: Parameter, usingRaw: boolean) {
    let v = this.getParameterValue(para, usingRaw);
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
      const fbg = svg.getElementById(`${this.id}-bg`);
      switch (para.acquisitionStatus) {
        case 'ACQUIRED':
          switch (para.monitoringResult) {
            case 'DISABLED':
              fbg.setAttribute('class', 'disabled-bg');
              ftxt.setAttribute('class', 'disabled-fg');
              break;

            case 'IN_LIMITS':
              fbg.setAttribute('class', 'in_limits-bg');
              ftxt.setAttribute('class', 'in_limits-fg');
              break;

            case 'WATCH':
            case 'WARNING':
            case 'DISTRESS':
              fbg.setAttribute('class', 'nominal_limit_violation-bg');
              ftxt.setAttribute('class', 'nominal_limit_violation-fg');
              break;

            case 'CRITICAL':
            case 'SEVERE':
              fbg.setAttribute('class', 'danger_limit_violation-bg');
              ftxt.setAttribute('class', 'danger_limit_violation-fg');
              break;

            default:
              fbg.setAttribute('class', 'undefined-bg');
              ftxt.setAttribute('class', 'undefined-fg');
              break;
          }
          break;

        case 'NOT_RECEIVED':
          fbg.setAttribute('class', 'dead-bg');
          ftxt.setAttribute('class', 'dead-fg');
          break;

        case 'INVALID':
          fbg.setAttribute('class', 'dead-bg');
          ftxt.setAttribute('class', 'dead-fg');
          break;

        case 'EXPIRED':
          fbg.setAttribute('class', 'expired-bg');
          ftxt.setAttribute('class', 'expired-fg');
          break;
      }
    }
  }

  updatePosition(para: Parameter, attribute: 'x' | 'y', usingRaw: boolean) {
    this.updatePositionByTranslation(`${this.id}-group`, para, attribute, usingRaw);
  }

  updateFillColor(para: Parameter, usingRaw: boolean) {
    if (!this.overrideDqi) {
      return;
    }
    const newcolor = this.getParameterValue(para, usingRaw);
    const fbg = this.svg.getElementById(`${this.id}-bg`);
    fbg.setAttribute('fill', newcolor);
  }
}
