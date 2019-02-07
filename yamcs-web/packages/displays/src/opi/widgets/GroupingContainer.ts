import { G, Rect } from '../../tags';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class GroupingContainer extends AbstractWidget {

  draw(g: G) {
    const wrapperG = new G({
      transform: `translate(${this.x},${this.y})`,
    });
    g.addChild(wrapperG);

    const rect = new Rect({
      x: 0,
      y: 0,
      width: this.width,
      height: this.height,
      fill: this.backgroundColor,
    });
    if (this.transparent) {
      rect.setAttribute('fill-opacity', '0');
    }
    wrapperG.addChild(rect);
    for (const widgetNode of utils.findChildren(this.node, 'widget')) {
      const widget = this.display.createWidget(widgetNode, this.absoluteX + this.x, this.absoluteY + this.y);
      if (widget) {
        widget.tag = widget.drawWidget();
        this.display.addWidget(widget, wrapperG);
      }
    }
  }
}
