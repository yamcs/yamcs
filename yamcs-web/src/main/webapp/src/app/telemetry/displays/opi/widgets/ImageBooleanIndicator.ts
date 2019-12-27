import { G, Image as ImageTag, Rect } from '../../tags';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class ImageBooleanIndicator extends AbstractWidget {

  private onImage: string;
  private offImage: string;
  private transparency: boolean;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    this.onImage = this.display.resolve(utils.parseStringChild(node, 'on_image'));
    this.offImage = this.display.resolve(utils.parseStringChild(node, 'off_image'));
    this.transparency = utils.parseBooleanChild(node, 'transparency', true);
  }

  draw(g: G) {
    if (!this.transparency) {
      g.addChild(new Rect({
        x: this.x,
        y: this.y,
        width: this.width,
        height: this.height,
        fill: this.backgroundColor,
        'pointer-events': 'none',
      }));
    }

    const onImageUrl = this.display.displayCommunicator.getObjectURL('displays', this.onImage);
    g.addChild(new ImageTag({
      id: `${this.id}-on`,
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      'xlink:href': onImageUrl,
      'opacity': 0,
    }));

    const offImageUrl = this.display.displayCommunicator.getObjectURL('displays', this.offImage);
    g.addChild(new ImageTag({
      id: `${this.id}-off`,
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      'xlink:href': offImageUrl,
    }));
  }
}
