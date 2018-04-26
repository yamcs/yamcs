import { G, Rect } from '../tags';
import Band from './Band';
import RenderContext from '../RenderContext';

/**
 * Separator 'band' that can be added to a timeline configuration to add space between the surrounding bands.
 */
export default class SpacerBand extends Band {

  static get type() {
    return 'SpacerBand';
  }

  renderBand(ctx: RenderContext) {
    return new G().addChild(
      new Rect({ // horizontal divider
        x: ctx.x + this.timeline.positionDate(this.timeline.loadStart),
        y: ctx.y + this.height,
        width: this.timeline.pointsBetween(this.timeline.loadStart, this.timeline.loadStop),
        height: 1,
        fill: '#d1d5da',
        'pointer-events': 'none'
      })
    );
  }

  renderSidebar(ctx: RenderContext) {
    return new Rect({ // horizontal divider
      x: ctx.x,
      y: ctx.y + this.height,
      width: this.style['sidebarWidth'],
      height: 1,
      fill: '#d1d5da',
    });
  }
}
