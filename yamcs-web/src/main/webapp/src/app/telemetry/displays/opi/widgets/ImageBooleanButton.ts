import { G, Image as ImageTag, Rect } from '../../tags';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class ImageBooleanButton extends AbstractWidget {

  private toggleButton: boolean;
  private pushActionIndex: number;
  private releaseActionIndex: number;

  private onImage: string;
  private offImage: string;
  private transparency: boolean;

  private toggled = false;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    this.onImage = this.display.resolve(utils.parseStringChild(node, 'on_image'));
    this.offImage = this.display.resolve(utils.parseStringChild(node, 'off_image'));
    this.transparency = utils.parseBooleanChild(node, 'transparency', true);
    this.toggleButton = utils.parseBooleanChild(node, 'toggle_button');
    this.pushActionIndex = utils.parseIntChild(node, 'push_action_index');
    if (this.toggleButton) {
      this.releaseActionIndex = utils.parseIntChild(node, 'released_action_index');
    }
  }

  draw(g: G) {
    g.setAttribute('cursor', 'pointer');

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

  afterDomAttachment() {
    const buttonEl = this.svg.getElementById(this.id);
    const onImageEl = this.svg.getElementById(`${this.id}-on`) as SVGElement;
    const offImageEl = this.svg.getElementById(`${this.id}-off`) as SVGElement;

    buttonEl.addEventListener('click', e => {
      this.executeAction(this.toggled ? this.releaseActionIndex : this.pushActionIndex);
      if (this.toggleButton) {
        this.toggled = !this.toggled;
      }
      e.preventDefault();
      return false;
    });

    buttonEl.addEventListener('mousedown', e => {
      if (this.toggleButton) {
        if (!this.toggled) {
          this.push(onImageEl, offImageEl);
        }
      } else {
        this.push(onImageEl, offImageEl);
      }
      e.preventDefault();
      return false;
    });

    // Prevent element selection
    buttonEl.addEventListener('mousemove', e => {
      e.preventDefault();
      return false;
    });

    buttonEl.addEventListener('mouseup', e => {
      if (this.toggleButton) {
        if (this.toggled) {
          this.release(onImageEl, offImageEl);
        }
      } else {
        this.release(onImageEl, offImageEl);
      }
      e.preventDefault();
      return false;
    });

    buttonEl.addEventListener('mouseout', e => {
      if (this.toggleButton) {
        if (this.toggled) {
          this.push(onImageEl, offImageEl);
        } else {
          this.release(onImageEl, offImageEl);
        }
      } else {
        this.release(onImageEl, offImageEl);
      }
      e.preventDefault();
      return false;
    });
  }

  private push(onImageEl: SVGElement, offImageEl: SVGElement) {
    onImageEl.setAttribute('opacity', '1');
    offImageEl.setAttribute('opacity', '0');
  }

  private release(onImageEl: SVGElement, offImageEl: SVGElement) {
    onImageEl.setAttribute('opacity', '0');
    offImageEl.setAttribute('opacity', '1');
  }
}
