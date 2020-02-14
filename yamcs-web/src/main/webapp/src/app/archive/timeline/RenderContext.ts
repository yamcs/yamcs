import Plugin from './Plugin';
import Point from './Point';
import { Tag } from './tags';

export const enum RenderSection {
  HEADER,
  BODY
}

export const enum PanMode {
  X,
  Y,
  XY,
  NONE
}

export default class RenderContext {
  x = 0;
  y = 0;

  /**
   * Used when the viewport SVG is in panned state
   */
  translation = new Point(0, 0);

  /**
   * Currently used for sidebar to indicate extra lead space inside contribution
   */
  paddingTop = 0;

  /**
   * Currently used for sidebar to indicate extra trail space inside contribution
   */
  paddingBottom = 0;

  headerHeight = 0;
  bodyHeight = 0;

  contributions: Plugin[];
  enabledContributions: Plugin[];
  enabledHeaderContributions: Plugin[];
  enabledBodyContributions: Plugin[];

  defsContentEls: Tag[] = [];
  filterContentEls: Tag[] = [];

  headerViewportEls: Tag[] = [];
  headerSidebarEls: Tag[] = [];

  bodyViewportEls: Tag[] = [];
  bodySidebarEls: Tag[] = [];

  underlayViewportEls: Tag[] = [];
  xUnderlayViewportEls: Tag[] = [];
  yUnderlayViewportEls: Tag[] = [];

  overlayViewportEls: Tag[] = [];
  xOverlayViewportEls: Tag[] = [];
  yOverlayViewportEls: Tag[] = [];

  constructor(contributions: Plugin[]) {
    this.contributions = contributions;
    this.enabledContributions = contributions.filter(c => c.enabled);
    this.enabledHeaderContributions = this.enabledContributions.filter(c => (c as any)['header']);
    this.enabledBodyContributions = this.enabledContributions.filter(c => !(c as any)['header']);
  }

  get totalHeight() {
    return this.headerHeight + this.bodyHeight;
  }

  getContributions(section: RenderSection) {
    if (section === RenderSection.BODY) {
      return this.enabledBodyContributions;
    } else if (section === RenderSection.HEADER) {
      return this.enabledHeaderContributions;
    } else {
      return [];
    }
  }

  getSectionHeight(section: RenderSection) {
    if (section === RenderSection.BODY) {
      return this.bodyHeight;
    } else if (section === RenderSection.HEADER) {
      return this.headerHeight;
    }
  }

  addDefs(...els: Tag[]) {
    this.defsContentEls.push(...els);
  }

  addFilters(...els: Tag[]) {
    this.filterContentEls.push(...els);
  }

  addToViewportSection(section: RenderSection, ...els: Tag[]) {
    if (section === RenderSection.BODY) {
      this.bodyViewportEls.push(...els);
    } else if (section === RenderSection.HEADER) {
      this.headerViewportEls.push(...els);
    }
  }

  addToViewportUnderlay(panMode: PanMode, ...els: Tag[]) {
    if (panMode === PanMode.XY) {
      this.underlayViewportEls.push(...els);
    } else if (panMode === PanMode.X) {
      this.xUnderlayViewportEls.push(...els);
    } else if (panMode === PanMode.Y) {
      this.yUnderlayViewportEls.push(...els);
    }
  }

  addToViewportOverlay(panMode: PanMode, ...els: Tag[]) {
    if (panMode === PanMode.XY) {
      this.overlayViewportEls.push(...els);
    } else if (panMode === PanMode.X) {
      this.xOverlayViewportEls.push(...els);
    } else if (panMode === PanMode.Y) {
      this.yOverlayViewportEls.push(...els);
    }
  }

  addToSidebar(section: RenderSection, ...els: Tag[]) {
    if (section === RenderSection.BODY) {
      this.bodySidebarEls.push(...els);
    } else if (section === RenderSection.HEADER) {
      this.headerSidebarEls.push(...els);
    }
  }
}
