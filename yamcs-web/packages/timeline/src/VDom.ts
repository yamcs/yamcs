import Point from './Point';
import RenderContext, { PanMode, RenderSection } from './RenderContext';
import Timeline from './Timeline';
import { Defs, G, Rect, Svg, Tag } from './tags';

/**
 * Top-level object for a generic (empty) Timeline. The Timeline is split
 * in different types of contributions. Some contributions are fixed, others
 * are timebased (and potentially pannable).
 *
 * An instance of this class is created only once, but dynamic parts of its content
 * are recreated on every new data event. Doing so allows us to install
 * event handlers only once, thereby reducing unresponsiveness between
 * different renderings.
 */
export default class VDom {

  /**
   * The root element of the generated SVG
   */
  readonly rootEl: SVGSVGElement;

  /**
   * An empty svg which is a direct child of rootEl.
   * This was introduced for reliable mouse position
   * determination cross-browser.
   */
  readonly mousePositionReferenceEl: SVGSVGElement;

  readonly headerSidebarEl: SVGElement;

  // These are all pannable
  readonly headerViewportEl: SVGGElement;
  readonly bodyViewportEl: SVGGElement;
  readonly bodySidebarEl: SVGGElement;
  readonly underlayViewportEl: SVGGElement;
  readonly xUnderlayViewportEl: SVGGElement;
  readonly yUnderlayViewportEl: SVGGElement;
  readonly overlayViewportEl: SVGGElement;
  readonly xOverlayViewportEl: SVGGElement;
  readonly yOverlayViewportEl: SVGGElement;

  private headerSidebarSvgWrapperEl: SVGElement;
  private headerViewportSvgWrapperEl: SVGElement;
  private bodySidebarSvgWrapperEl: SVGElement;
  private bodyViewportSvgWrapperEl: SVGSVGElement;

  private underlayViewportSvgWrapperEl: SVGSVGElement;
  private xUnderlayViewportSvgWrapperEl: SVGSVGElement;
  private yUnderlayViewportSvgWrapperEl: SVGSVGElement;

  private overlayViewportSvgWrapperEl: SVGSVGElement;
  private xOverlayViewportSvgWrapperEl: SVGSVGElement;
  private yOverlayViewportSvgWrapperEl: SVGSVGElement;

  private defsEl: SVGElement;
  private filterEls: SVGElement[] = [];

  constructor(private timeline: Timeline, private style: any) {

    this.rootEl = new Svg({
      width: '100%',
      height: '100%',
      style: 'width: 100%; height: 100%; overflow: hidden',
      'font-family': style['fontFamily'],
    }).toDomElement() as SVGSVGElement;

    this.defsEl = new Defs().toDomElement();
    this.rootEl.appendChild(this.defsEl);

    this.mousePositionReferenceEl = new Svg().toDomElement() as SVGSVGElement;
    this.rootEl.appendChild(this.mousePositionReferenceEl);

    // Static underlay

    this.underlayViewportSvgWrapperEl = new Svg({
      class: 'ts-underlay-vp',
      style: 'overflow: visible'
    }).toDomElement() as SVGSVGElement;
    this.rootEl.appendChild(this.underlayViewportSvgWrapperEl);
    this.underlayViewportEl = new G().toDomElement() as SVGGElement;
    this.underlayViewportSvgWrapperEl.appendChild(this.underlayViewportEl);

    this.xUnderlayViewportSvgWrapperEl = new Svg({
      class: 'ts-underlay-x-vp',
      style: 'overflow: visible'
    }).toDomElement() as SVGSVGElement;
    this.rootEl.appendChild(this.xUnderlayViewportSvgWrapperEl);
    this.xUnderlayViewportEl = new G().toDomElement() as SVGGElement;
    this.xUnderlayViewportSvgWrapperEl.appendChild(this.xUnderlayViewportEl);

    this.yUnderlayViewportSvgWrapperEl = new Svg({
      class: 'ts-underlay-y-vp',
      style: 'overflow: visible'
    }).toDomElement() as SVGSVGElement;
    this.rootEl.appendChild(this.yUnderlayViewportSvgWrapperEl);
    this.yUnderlayViewportEl = new G().toDomElement() as SVGGElement;
    this.yUnderlayViewportSvgWrapperEl.appendChild(this.yUnderlayViewportEl);

    // vp needs to be in a 'g' to make transforms work. So wrap it up
    this.bodyViewportSvgWrapperEl = new Svg({
      class: 'ts-body-vp',
      style: 'overflow: hidden',
    }).toDomElement() as SVGSVGElement;
    this.rootEl.appendChild(this.bodyViewportSvgWrapperEl);
    this.bodyViewportEl = new G().toDomElement() as SVGGElement;
    this.bodyViewportSvgWrapperEl.appendChild(this.bodyViewportEl);

    // vp needs to be in a 'g' to make transforms work. So wrap it up
    this.headerViewportSvgWrapperEl = new Svg({
      class: 'ts-header-vp',
      style: 'overflow: hidden',
    }).toDomElement();
    this.rootEl.appendChild(this.headerViewportSvgWrapperEl);
    this.headerViewportEl = new G().toDomElement() as SVGGElement;
    this.headerViewportSvgWrapperEl.appendChild(this.headerViewportEl);

    this.overlayViewportSvgWrapperEl = new Svg({
      class: 'ts-overlay-vp',
      style: 'overflow: visible'
    }).toDomElement() as SVGSVGElement;
    this.rootEl.appendChild(this.overlayViewportSvgWrapperEl);
    this.overlayViewportEl = new G().toDomElement() as SVGGElement;
    this.overlayViewportSvgWrapperEl.appendChild(this.overlayViewportEl);

    this.xOverlayViewportSvgWrapperEl = new Svg({
      class: 'ts-overlay-x-vp',
      style: 'overflow: visible'
    }).toDomElement() as SVGSVGElement;
    this.rootEl.appendChild(this.xOverlayViewportSvgWrapperEl);
    this.xOverlayViewportEl = new G().toDomElement() as SVGGElement;
    this.xOverlayViewportSvgWrapperEl.appendChild(this.xOverlayViewportEl);

    this.yOverlayViewportSvgWrapperEl = new Svg({
      class: 'ts-overlay-y-vp',
      style: 'overflow: visible'
    }).toDomElement() as SVGSVGElement;
    this.rootEl.appendChild(this.yOverlayViewportSvgWrapperEl);
    this.yOverlayViewportEl = new G().toDomElement() as SVGGElement;
    this.yOverlayViewportSvgWrapperEl.appendChild(this.yOverlayViewportEl);

    const sidebarBg = new G().addChild(new Rect({
      x: 0,
      y: 0,
      height: '100%',
      width: this.timeline.getSidebarWidth(),
      fill: style.sidebarBackgroundColor,
    }), new Rect({ // Divider with band area
      x: this.timeline.getSidebarWidth(),
      y: 0,
      width: 1,
      height: '100%',
      fill: style.dividerColor,
    }));
    this.rootEl.appendChild(sidebarBg.toDomElement());

    this.bodySidebarSvgWrapperEl = new Svg({
      class: 'ts-body-sb',
      style: 'overflow: hidden',
      width: this.timeline.getSidebarWidth(),
    }).toDomElement();
    this.rootEl.appendChild(this.bodySidebarSvgWrapperEl);
    this.bodySidebarEl = new G().toDomElement() as SVGGElement;
    this.bodySidebarSvgWrapperEl.appendChild(this.bodySidebarEl);

    this.headerSidebarSvgWrapperEl = new Svg({
      class: 'ts-header-sb',
      style: 'overflow: hidden',
      width: this.timeline.getSidebarWidth(),
    }).toDomElement();
    this.rootEl.appendChild(this.headerSidebarSvgWrapperEl);
    this.headerSidebarEl = new G().toDomElement();
    this.headerSidebarSvgWrapperEl.appendChild(this.headerSidebarEl);
  }

  rebuild(translation = new Point(0, 0)): RenderContext {
    const ctx = this.calculateContentModel(translation);
    this.defsEl.innerHTML = '';
    this.headerViewportEl.innerHTML = '';
    this.headerSidebarEl.innerHTML = '';
    this.bodyViewportEl.innerHTML = '';
    this.bodySidebarEl.innerHTML = '';
    this.underlayViewportEl.innerHTML = '';
    this.xUnderlayViewportEl.innerHTML = '';
    this.yUnderlayViewportEl.innerHTML = '';
    this.overlayViewportEl.innerHTML = '';
    this.xOverlayViewportEl.innerHTML = '';
    this.yOverlayViewportEl.innerHTML = '';
    for (const filterEl of this.filterEls) {
      this.rootEl.removeChild(filterEl);
    }
    this.filterEls = [];

    for (const el of ctx.defsContentEls) {
      this.defsEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.filterContentEls) {
      const filterEl = el.toDomElement();
      this.rootEl.appendChild(filterEl);
      this.filterEls.push(filterEl);
    }

    for (const el of ctx.underlayViewportEls) {
      this.underlayViewportEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.xUnderlayViewportEls) {
      this.xUnderlayViewportEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.yUnderlayViewportEls) {
      this.yUnderlayViewportEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.headerViewportEls) {
      this.headerViewportEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.headerSidebarEls) {
      this.headerSidebarEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.bodyViewportEls) {
      this.bodyViewportEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.bodySidebarEls) {
      this.bodySidebarEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.overlayViewportEls) {
      this.overlayViewportEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.xOverlayViewportEls) {
      this.xOverlayViewportEl.appendChild(el.toDomElement());
    }
    for (const el of ctx.yOverlayViewportEls) {
      this.yOverlayViewportEl.appendChild(el.toDomElement());
    }

    // Adjust fixed elements to new reality
    this.headerViewportSvgWrapperEl.setAttribute('x', String(this.timeline.getSidebarWidth()));

    this.bodyViewportSvgWrapperEl.setAttribute('x', String(this.timeline.getSidebarWidth()));
    this.bodyViewportSvgWrapperEl.setAttribute('y', String(ctx.headerHeight));

    this.bodySidebarSvgWrapperEl.setAttribute('y', String(ctx.headerHeight));

    return ctx;
  }

  /**
   * Builds up the rendered elements
   */
  private calculateContentModel(translation: Point) {
    const ctx = new RenderContext(this.timeline.contributions);
    ctx.translation = translation;

    // Calculate full heights, before rendering anything. Some contributions may need this information
    for (let i = 0; i < ctx.enabledContributions.length; i++) {
      const contribution = ctx.enabledContributions[i];
      if ((contribution as any)['header']) {
        ctx.headerHeight += contribution.height;
        if (i !== 0 && contribution.height > 0) {
          ctx.headerHeight += this.style['bandDividerHeight'];
        }
      } else {
        ctx.bodyHeight += contribution.height;
        if (i !== 0 && contribution.height > 0) {
          ctx.bodyHeight += this.style['bandDividerHeight'];
        }
      }
    }

    ctx.addDefs(...this.renderDefs());
    ctx.addFilters(...this.renderFilters());

    for (const contribution of ctx.enabledContributions) {
      ctx.addToViewportUnderlay(PanMode.XY, contribution.renderViewportUnderlay(ctx));
      ctx.addToViewportUnderlay(PanMode.X, contribution.renderViewportUnderlay(ctx));
      ctx.addToViewportUnderlay(PanMode.Y, contribution.renderViewportUnderlay(ctx));
    }

    this.renderSection(ctx, RenderSection.HEADER);
    this.renderSection(ctx, RenderSection.BODY);

    for (const contribution of ctx.enabledContributions) {
      ctx.addToViewportOverlay(PanMode.XY, contribution.renderViewportOverlay(ctx));
      ctx.addToViewportOverlay(PanMode.X, contribution.renderViewportXOverlay(ctx));
      ctx.addToViewportOverlay(PanMode.Y, contribution.renderViewportYOverlay(ctx));
    }

    return ctx;
  }

  private renderSection(ctx: RenderContext, section: RenderSection) {
    // Sections are wrapped in svg with custom x/y coords
    ctx.x = 0;
    ctx.y = 0;

    for (const contribution of ctx.getContributions(section)) {
      contribution.renderSection(ctx, section);

      let contributionHeight = contribution.height;
      if (contributionHeight > 0) { // Account for border
        contributionHeight += this.style['bandDividerHeight'];
      }
      ctx.y += contributionHeight;
    }
  }

  private renderDefs(): Tag[] {
    const defs = this.timeline.theme['defs'] || [];
    for (const contribution of this.timeline.contributions) {
      defs.push(...contribution.renderDefs());
    }
    return defs;
  }

  private renderFilters(): Tag[] {
    const filters = this.timeline.theme['filters'] || [];
    for (const contribution of this.timeline.contributions) {
      filters.push(...contribution.renderFilters());
    }
    return filters;
  }
}
