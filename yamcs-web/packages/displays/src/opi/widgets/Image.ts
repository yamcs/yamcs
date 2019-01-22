import { G, Image as ImageTag, Rect } from '../../tags';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class Image extends AbstractWidget {

  private imageFile: string;
  private transparency: boolean;

  constructor(node: Element, display: OpiDisplay) {
    super(node, display);
    this.imageFile = utils.parseStringChild(node, 'image_file');
    this.transparency = utils.parseBooleanChild(node, 'transparency');
  }

  draw(g: G) {
    if (!this.transparency) {
      g.addChild(new Rect({
        x: this.x,
        y: this.y,
        width: this.width,
        height: this.height,
        fill: this.backgroundColor,
      }));
    }

    const imageUrl = this.display.displayCommunicator.getObjectURL('displays', this.imageFile);
    g.addChild(new ImageTag({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      'xlink:href': imageUrl,
    }));
  }
}
