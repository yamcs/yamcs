import { G, Rect } from '../tags';
import { isAfter, isBefore, toDate } from '../utils';
import RenderContext from '../RenderContext';
import DayNightBand from './DayNightBand';
import Timeline from '../Timeline';
import Band from '../core/Band';

/**
 * Renderer to be added to a band contribution.
 * Will highlight orbital night periods based on another contribution of type DayNightBand
 */
export default class DayNightBackgroundAddon {

  renderViewportOverlay(ctx: RenderContext, contribution: Band, timeline: Timeline) {
    const g = new G();
    for (const c of ctx.contributions) {
      if (c instanceof DayNightBand) {
        const events = c.opts.events || [];
        for (const event of events) {
          if (!event.day) {
            const start = toDate(event.start);
            const stop = toDate(event.stop);
            if (isBefore(start, timeline.loadStop) && isAfter(stop, timeline.loadStart)) {
              g.addChild(new Rect({
                x: ctx.x + timeline.positionDate(start),
                y: ctx.y,
                width: timeline.pointsBetween(start, stop),
                height: contribution.height,
                fill: 'black',
                'fill-opacity': 0.08,
                'pointer-events': 'none',
              }));
            }
          }
        }
      }
    }
    return g;
  }
}
