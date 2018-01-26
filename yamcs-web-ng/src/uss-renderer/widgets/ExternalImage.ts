import { AbstractWidget } from './AbstractWidget';
import * as utils from '../utils';
import { Image } from '../tags';

export class ExternalImage extends AbstractWidget {

  parseAndDraw() {
    const pathname = utils.parseStringChild(this.node, 'Pathname');

    return new Image({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      class: 'external-image',
      'data-name': this.name,
      'xlink:href': `/_static/${pathname}`
    });
  }

  updateProperty(property: string, value: any, acquisitionStatus: string, monitoringResult: string) {
    console.warn('Unsupported dynamic property: ' + property);
  }
}
