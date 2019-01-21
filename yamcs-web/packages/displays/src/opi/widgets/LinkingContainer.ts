import { G, Rect } from '../../tags';
import { AbstractWidget } from './AbstractWidget';

export class LinkingContainer extends AbstractWidget {

  parseAndDraw() {
    const g = new G({
      class: 'linking-container',
      'data-name': this.name,
    });

    const rect = new Rect({
      x: this.x,
      y: this.y,
      width: this.width,
      height: this.height,
      fill: this.backgroundColor.toString(),
      ...this.borderStyle,
      'pointer-events': 'none',
    });
    if (this.transparent) {
      rect.setAttribute('fill-opacity', '0');
    }
    g.addChild(rect);

    return g;
  }
}
