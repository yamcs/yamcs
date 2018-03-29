import { Color } from './Color';
import * as utils from './utils';

export interface Style {
  acquisitionRules: string[];
  monitoringRules: string[];
  fg: Color;
  bg: Color;
  flags: string;
}

export const DEFAULT_STYLE: Style = {
  acquisitionRules: [],
  monitoringRules: [],
  fg: Color.BLACK,
  bg: Color.WHITE,
  flags: '  ',
};

export class StyleSet {
  lowCautionLimit: Color;
  highCautionLimit: Color;
  lowWarningLimit: Color;
  highWarningLimit: Color;
  lowOffScaleWarningLimit: Color;
  highOffScaleWarningLimit: Color;

  styles: Style[];

  constructor(xmlDoc: XMLDocument) {
    const root = xmlDoc.getElementsByTagName('StyleSet')[0];
    const graphLimitColors = utils.findChild(root, 'GraphLimitColors');

    const lowCautionLimitNode = utils.findChild(graphLimitColors, 'LowCautionLimit');
    this.lowCautionLimit = Color.forName(utils.parseStringAttribute(lowCautionLimitNode, 'color'));

    const highCautionLimitNode = utils.findChild(graphLimitColors, 'HighCautionLimit');
    this.highCautionLimit = Color.forName(utils.parseStringAttribute(highCautionLimitNode, 'color'));

    const lowWarningLimitNode = utils.findChild(graphLimitColors, 'LowWarningLimit');
    this.lowWarningLimit = Color.forName(utils.parseStringAttribute(lowWarningLimitNode, 'color'));

    const highWarningLimitNode = utils.findChild(graphLimitColors, 'HighWarningLimit');
    this.highWarningLimit = Color.forName(utils.parseStringAttribute(highWarningLimitNode, 'color'));

    const lowOffScaleWarningLimitNode = utils.findChild(graphLimitColors, 'LowOffScaleWarningLimit');
    this.lowOffScaleWarningLimit = Color.forName(utils.parseStringAttribute(lowOffScaleWarningLimitNode, 'color'));

    const highOffScaleWarningLimitNode = utils.findChild(graphLimitColors, 'HighOffScaleWarningLimit');
    this.highOffScaleWarningLimit = Color.forName(utils.parseStringAttribute(highOffScaleWarningLimitNode, 'color'));

    this.styles = [];
    for (const styleNode of utils.findChildren(root, 'Style')) {
      const acquisitionRules: string[] = [];
      const acquisition = utils.parseStringAttribute(styleNode, 'acquisition');
      for (const rule of acquisition.split(',')) {
        acquisitionRules.push(rule.trim());
      }

      const monitoringRules: string[] = [];
      const monitoring = utils.parseStringAttribute(styleNode, 'monitoring');
      for (const rule of monitoring.split(',')) {
        monitoringRules.push(rule.trim());
      }

      const style = {
        acquisitionRules,
        monitoringRules,
        fg: Color.forName(utils.parseStringAttribute(styleNode, 'fg')),
        bg: Color.forName(utils.parseStringAttribute(styleNode, 'bg')),
        flags: utils.parseStringAttribute(styleNode, 'flags'),
        desc: utils.parseStringAttribute(styleNode, 'desc'),
      };
      this.styles.push(style);
    }
  }

  getStyle(acquisitionStatus: string, monitoringResult?: string) {
    for (const style of this.styles) {
      for (const acquisitionRule of style.acquisitionRules) {
        if (acquisitionRule === acquisitionStatus || acquisitionRule === '*') {
          for (const monitoringRule of style.monitoringRules) {
            if (monitoringRule === monitoringResult || monitoringRule === '*') {
              return style;
            }
          }
        }
      }
    }
    return DEFAULT_STYLE;
  }
}
