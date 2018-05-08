import Timeline from './Timeline';
import RenderContext, { RenderSection } from './RenderContext';
import { G, Tag } from './tags';
import { Action } from './Action';

export interface PluginOptions {
  addons?: any[];
  enabled?: boolean;
}

export default abstract class Plugin {

  addons: any[];
  enabled: boolean;

  constructor(
    protected timeline: Timeline,
    protected opts: PluginOptions,
    protected style: any,
  ) {
    this.addons = opts.addons || [];
    this.enabled = (opts.enabled !== false);
  }

  get height(): number {
    return 0;
  }

  renderDefs(): Tag[] {
    return [];
  }

  renderFilters(): Tag[] {
    return [];
  }

  renderSection(ctx: RenderContext, section: RenderSection) {
    ctx.x -= ctx.translation.x;
    ctx.addToViewportSection(section, this.renderViewport(ctx));
    ctx.x += ctx.translation.x;
  }

  renderViewport(ctx: RenderContext): Tag {
    return new G();
  }

  renderViewportUnderlay(ctx: RenderContext): Tag {
    return new G();
  }

  renderViewportXUnderlay(ctx: RenderContext): Tag {
    return new G();
  }

  renderViewportYUnderlay(ctx: RenderContext): Tag {
    return new G();
  }

  renderViewportOverlay(ctx: RenderContext): Tag {
    return new G();
  }

  renderViewportXOverlay(ctx: RenderContext): Tag {
    return new G();
  }

  renderViewportYOverlay(ctx: RenderContext): Tag {
    return new G();
  }

  /*
   * Lifecycle hooks
   */
  postRender(ctx: RenderContext, rootElement: SVGSVGElement) {
    // NOP
  }

  onAction(id: string, action: Action) {
    // NOP
  }

  tearDown() {
    // NOP
  }
}
