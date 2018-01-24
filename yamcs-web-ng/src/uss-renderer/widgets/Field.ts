import * as utils from '../utils';

const sprintf = require('sprintf-js').sprintf;


import { AbstractWidget } from './AbstractWidget';
import { G, Rect, Text, ClipPath } from '../tags';
import { ParameterUpdate } from '../ParameterUpdate';

export class Field extends AbstractWidget {

  decimals: number;
  format: string | null;

  overrideDqi: boolean;

  private fieldEl: Element;
  private fieldBackgroundEl: Element;
  private fieldTextEl: Element;

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
      'shape-rendering': 'crispEdges',
    });

    for (const binding of this.parameterBindings) {
      if (binding.dynamicProperty === 'VALUE' && binding.opsName) {
          const yamcsInstance = 'dev'; // TODO window.location.pathname.match(/\/([^\/]*)\/?/)[1];
          rect.setAttribute('xlink:href', `/${yamcsInstance}/mdb/MDB:OPS Name/${binding.opsName}`);
      }
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
    const fontFamily = text.attributes['font-family'];
    const fontSize = Number(text.attributes['font-size']);
    const fm = this.getFontMetrics(/*innerText*/ '', fontFamily, fontSize);

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

  afterDomAttachment() {
    this.fieldEl = this.svg.getElementById(`${this.id}-group`);
    this.fieldBackgroundEl = this.svg.getElementById(`${this.id}-bg`);
    this.fieldTextEl = this.svg.getElementById(this.id);
  }

  updateValue(parameterUpdate: ParameterUpdate, usingRaw: boolean) {
    let v = this.getParameterValue(parameterUpdate, usingRaw);
    if (typeof v === 'number') {
      if (this.format) {
        v = sprintf(this.format, v);
      } else {
        v = v.toFixed(this.decimals);
      }
    }
    this.fieldTextEl.textContent = v;
    if (!this.overrideDqi) {
      switch (parameterUpdate.acquisitionStatus) {
        case 'ACQUIRED':
          switch (parameterUpdate.monitoringResult) {
            case 'DISABLED':
              this.fieldBackgroundEl.setAttribute('class', 'disabled-bg');
              this.fieldTextEl.setAttribute('class', 'disabled-fg');
              break;

            case 'IN_LIMITS':
              this.fieldBackgroundEl.setAttribute('class', 'in_limits-bg');
              this.fieldTextEl.setAttribute('class', 'in_limits-fg');
              break;

            case 'WATCH':
            case 'WARNING':
            case 'DISTRESS':
              this.fieldBackgroundEl.setAttribute('class', 'nominal_limit_violation-bg');
              this.fieldTextEl.setAttribute('class', 'nominal_limit_violation-fg');
              break;

            case 'CRITICAL':
            case 'SEVERE':
              this.fieldBackgroundEl.setAttribute('class', 'danger_limit_violation-bg');
              this.fieldTextEl.setAttribute('class', 'danger_limit_violation-fg');
              break;

            default:
              this.fieldBackgroundEl.setAttribute('class', 'undefined-bg');
              this.fieldTextEl.setAttribute('class', 'undefined-fg');
              break;
          }
          break;

        case 'NOT_RECEIVED':
          this.fieldBackgroundEl.setAttribute('class', 'dead-bg');
          this.fieldTextEl.setAttribute('class', 'dead-fg');
          break;

        case 'INVALID':
          this.fieldBackgroundEl.setAttribute('class', 'dead-bg');
          this.fieldTextEl.setAttribute('class', 'dead-fg');
          break;

        case 'EXPIRED':
          this.fieldBackgroundEl.setAttribute('class', 'expired-bg');
          this.fieldTextEl.setAttribute('class', 'expired-fg');
          break;
      }
    }
  }

  updatePosition(parameterUpdate: ParameterUpdate, attribute: 'x' | 'y', usingRaw: boolean) {
    if (attribute === 'x') {
      this.x = this.getParameterValue(parameterUpdate, usingRaw);
    } else if (attribute === 'y') {
      this.y = this.getParameterValue(parameterUpdate, usingRaw);
    }
    this.fieldEl.setAttribute('transform', `translate(${this.x},${this.y})`);
  }

  updateFillColor(parameterUpdate: ParameterUpdate, usingRaw: boolean) {
    if (!this.overrideDqi) {
      return;
    }
    const newColor = this.getParameterValue(parameterUpdate, usingRaw);
    this.fieldBackgroundEl.setAttribute('fill', newColor);
  }
}
