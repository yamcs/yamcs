import { Action } from '../Action';
import { SidebarEvent } from '../events';
import Plugin, { PluginOptions } from '../Plugin';
import RenderContext, { RenderSection } from '../RenderContext';
import { Defs, G, Rect, Set, Svg, Tag, Text } from '../tags';
import Timeline from '../Timeline';
import { generateId } from '../utils';

export interface BandOptions extends PluginOptions {
  id: string;
  interactive?: boolean;
  interactiveSidebar?: boolean;
  label?: string;
}

export default abstract class Band extends Plugin {

  private sidebarId: string;

  constructor(timeline: Timeline, protected opts: BandOptions, style: any) {
    super(timeline, opts, style);
  }

  get height() {
    return this.style.lineHeight;
  }

  renderSection(ctx: RenderContext, section: RenderSection) {
    ctx.addToSidebar(section, this.renderSidebar(ctx));

    ctx.x -= ctx.translation.x;
    this.renderViewportSection(ctx, section);
    ctx.x += ctx.translation.x;
  }

  renderViewportSection(ctx: RenderContext, section: RenderSection) {
    ctx.addToViewportSection(section, this.renderBand(ctx));

    if (this.timeline.domReduction && !this.opts.interactive) {
      ctx.addToViewportSection(section, this.renderViewportAsImage(ctx));
    } else {
      ctx.addToViewportSection(section, this.renderViewport(ctx));
    }

    for (const addon of this.addons) {
      const els = addon.renderViewportOverlay(ctx, this, this.timeline);
      ctx.addToViewportSection(section, els);
    }
  }

  private renderViewportAsImage(ctx: RenderContext): Tag {
    const origX = ctx.x;
    const origY = ctx.y;

    // Remove any translation effects before rendering the viewport
    // (instead, the image itself is translated)
    ctx.x = 0;
    ctx.y = 0;

    // In the SVG x=0 matches visibleStart. In the result image x=0 must match loadStart
    ctx.x += this.timeline.pointsBetween(this.timeline.loadStart, this.timeline.visibleStart);

    const svg = new Svg({
      'font-family': this.style.fontFamily
    });

    // Image cannot have external dependencies, so copy root defs
    const defs = new Defs();
    defs.addChild(...ctx.defsContentEls);
    svg.addChild(defs);

    svg.addChild(this.renderViewport(ctx));

    ctx.x = origX;
    ctx.y = origY;

    return svg.asImage({
      x: origX + this.timeline.positionDate(this.timeline.loadStart),
      y: origY,
      width: this.timeline.pointsBetween(this.timeline.loadStart, this.timeline.loadStop),
      height: this.height,
      'pointer-events': 'none',
    });
  }

  renderBand(ctx: RenderContext): Tag {
    const loadWidth = this.timeline.pointsBetween(this.timeline.loadStart, this.timeline.loadStop);
    return new G().addChild(
      new Rect({
        x: ctx.x + this.timeline.positionDate(this.timeline.loadStart),
        y: ctx.y,
        width: loadWidth,
        height: this.height,
        fill: this.style.bandBackgroundColor,
        'pointer-events': 'none'
      }),
      new Rect({ // horizontal divider
        x: ctx.x + this.timeline.positionDate(this.timeline.loadStart),
        y: ctx.y + this.height,
        width: loadWidth,
        height: 1,
        fill: this.style.dividerColor,
        'pointer-events': 'none'
      })
    );
  }

  renderSidebar(ctx: RenderContext): Tag {
    this.sidebarId = generateId();
    const bg = new Rect({
      id: this.sidebarId,
      x: ctx.x,
      y: ctx.y,
      width: '100%',
      height: this.height + ctx.paddingTop + ctx.paddingBottom,
      cursor: 'default',
      fill: 'transparent',
    });

    if (this.opts.interactiveSidebar) {
      bg.setAttribute('cursor', 'pointer');
      bg.addChild(new Set({
        attributeName: 'fill',
        to: this.style.sidebarHoverBackgroundColor,
        begin: 'mouseover',
        end: 'mouseout',
      }));
      this.timeline.registerActionTarget('click', this.sidebarId);
      this.timeline.registerActionTarget('contextmenu', this.sidebarId);
      this.timeline.registerActionTarget('mouseenter', this.sidebarId);
      this.timeline.registerActionTarget('mouseleave', this.sidebarId);
    }

    const g = new G().addChild(
      bg,
      new Rect({ // horizontal divider
        x: ctx.x,
        y: ctx.y + this.height,
        width: '100%',
        height: 1,
        fill: this.style.dividerColor,
        'pointer-events': 'none',
      })
    );

    if (this.opts.label) {
      g.addChild(new Text({
        x: ctx.x + 5,
        y: ctx.y + ctx.paddingTop + (this.height / 2),
        fill: this.style.sidebarForegroundColor,
        'pointer-events': 'none',
        'text-anchor': 'left',
        'dominant-baseline': 'middle',
        'font-size': this.style.textSize,
      }, this.opts.label || 'Untitled'));
    }

    return g;
  }

  onAction(id: string, action: Action) {
    if (id === this.sidebarId) {
      switch (action.type) {
        case 'click':
          const sidebarClickEvent = new SidebarEvent(this.opts, action.target);
          sidebarClickEvent.clientX = action.clientX;
          sidebarClickEvent.clientY = action.clientY;
          this.timeline.fireEvent('sidebarClick', sidebarClickEvent);
          break;
        case 'contextmenu':
          const sidebarContextMenuEvent = new SidebarEvent(this.opts, action.target);
          sidebarContextMenuEvent.clientX = action.clientX;
          sidebarContextMenuEvent.clientY = action.clientY;
          this.timeline.fireEvent('sidebarContextMenu', sidebarContextMenuEvent);
          break;
        case 'mouseenter':
          if (!action.grabbing) {
            const mouseEnterEvent = new SidebarEvent(this.opts, action.target);
            mouseEnterEvent.clientX = action.clientX;
            mouseEnterEvent.clientY = action.clientY;
            this.timeline.fireEvent('sidebarMouseEnter', mouseEnterEvent);
          }
          break;
        case 'mouseleave':
          const mouseLeaveEvent = new SidebarEvent(this.opts, action.target);
          mouseLeaveEvent.clientX = action.clientX;
          mouseLeaveEvent.clientY = action.clientY;
          this.timeline.fireEvent('sidebarMouseLeave', mouseLeaveEvent);
          break;
      }
    }
  }
}
