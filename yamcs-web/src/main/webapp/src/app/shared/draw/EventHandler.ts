import { HitCanvas, HitRegionSpecification } from './HitCanvas';
import { Point } from './positioning';

// Compare by id instead of references. HitRegions are allowed to be generated
// on each draw, whereas the "id" could be something more long-term.
function regionMatches(region1?: HitRegionSpecification, region2?: HitRegionSpecification) {
  return region1 && region2 && region1.id === region2.id;
}

export class EventHandler {

  private prevEnteredRegion?: HitRegionSpecification;

  constructor(private canvas: HTMLCanvasElement, private hitCanvas: HitCanvas) {
    canvas.addEventListener('click', e => this.onClick(e), false);
    canvas.addEventListener('mousedown', e => this.onMouseDown(e), false);
    canvas.addEventListener('mouseup', e => this.onMouseUp(e), false);
    canvas.addEventListener('mouseout', e => this.onMouseOut(e), false);
    canvas.addEventListener('mousemove', e => this.onMouseMove(e), false);
  }

  private toPoint(event: MouseEvent): Point {
    const bbox = this.canvas.getBoundingClientRect();
    return { x: event.clientX - bbox.left, y: event.clientY - bbox.top };
  }

  private onClick(event: MouseEvent) {
    const point = this.toPoint(event);
    const region = this.hitCanvas.getActiveRegion(point.x, point.y);
    if (region && region.click) {
      region.click();
    }
  }

  private onMouseDown(event: MouseEvent) {
    const point = this.toPoint(event);
    const region = this.hitCanvas.getActiveRegion(point.x, point.y);
    if (region && region.mouseDown) {
      region.mouseDown();
    }
  }

  private onMouseUp(event: MouseEvent) {
    const point = this.toPoint(event);
    const region = this.hitCanvas.getActiveRegion(point.x, point.y);
    if (region && region.mouseUp) {
      region.mouseUp();
    }
  }

  private onMouseOut(event: MouseEvent) {
  }

  private onMouseMove(event: MouseEvent) {
    const point = this.toPoint(event);
    const region = this.hitCanvas.getActiveRegion(point.x, point.y);

    if (this.prevEnteredRegion && this.prevEnteredRegion.mouseOut) {
      if (!regionMatches(this.prevEnteredRegion, region)) {
        this.prevEnteredRegion.mouseOut();
      }
    }

    if (region && region.mouseEnter) {
      if (!regionMatches(this.prevEnteredRegion, region)) {
        region.mouseEnter();
      }
    }
    if (region && region.mouseMove) {
      const pressing = !!event.which;
      region.mouseMove(pressing);
    }

    this.prevEnteredRegion = region;

    const cursor = (region && region.cursor) ? region.cursor : 'auto';
    if (cursor != this.canvas.style.cursor) {
      this.canvas.style.cursor = cursor;
    }
  }
}
