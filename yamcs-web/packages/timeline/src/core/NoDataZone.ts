import Plugin from '../Plugin';
import RenderContext from '../RenderContext';
import { G, Rect } from '../tags';

/**
 * Highlights the horizontal time ranges where no data is available.
 * Only non-spacer bands are covered, to increase the look near the sidebar
 */
export default class NoDataZone extends Plugin {

  static get type(): string {
    return 'NoDataZone';
  }

  static get rules() {
    return {
      backgroundColor: '#fff',
      backgroundFilter: 'crossHatch',
      dark: {
        backgroundColor: '#2b2b2b',
        backgroundFilter: 'darkHatch',
      },
    };
  }

  renderViewport(ctx: RenderContext) {
    const g = new G();
    g.addChild(new Rect({
      x: ctx.x + this.timeline.positionDate(this.timeline.loadStop),
      y: 0,
      width: 15000,
      height: '100%',
      fill: this.style.backgroundColor,
      'pointer-events': 'none',
    }));
    g.addChild(new Rect({
      x: ctx.x + this.timeline.positionDate(this.timeline.loadStop),
      y: 0,
      width: 15000,
      height: '100%',
      fill: `url(#${this.style.backgroundFilter})`,
      'pointer-events': 'none',
    }));
    g.addChild(new Rect({
      x: ctx.x + this.timeline.positionDate(this.timeline.loadStart) - 15000,
      y: 0,
      width: 15000,
      height: '100%',
      fill: this.style.backgroundColor,
      'pointer-events': 'none',
    }));
    g.addChild(new Rect({
      x: ctx.x + this.timeline.positionDate(this.timeline.loadStart) - 15000,
      y: 0,
      width: 15000,
      height: '100%',
      fill: `url(#${this.style.backgroundFilter})`,
      'pointer-events': 'none',
    }));

    return g;
  }
}
