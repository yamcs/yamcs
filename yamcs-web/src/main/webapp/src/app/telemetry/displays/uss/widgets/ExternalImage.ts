import { Image } from '../../tags';
import { DataSourceBinding } from '../DataSourceBinding';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class ExternalImage extends AbstractWidget {

  parseAndDraw() {
    const pathname = utils.parseStringChild(this.node, 'Pathname');
    const imageUrl = this.display.displayCommunicator.getObjectURL('displays', pathname);

    return new Image({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      class: 'external-image',
      'data-name': this.name,
      'xlink:href': imageUrl,
    });
  }

  registerBinding(binding: DataSourceBinding) {
    console.warn('Unsupported binding to property: ' + binding.dynamicProperty);
  }
}
