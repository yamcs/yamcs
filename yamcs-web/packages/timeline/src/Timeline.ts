import { Action, ActionType } from './Action';
import EventBand from './core/EventBand';
import HorizontalSelection from './core/HorizontalSelection';
import LocationTracker from './core/LocationTracker';
import NoDataZone from './core/NoDataZone';
import SpacerBand from './core/SpacerBand';
import Timescale from './core/Timescale';
import WallclockLocator from './core/WallclockLocator';
import EventHandling from './EventHandling';
import { LoadRangeEvent, RangeSelectionChangedEvent, TimelineEventMap, ViewportChangedEvent } from './events';
import { PanMode, TimelineOptions, TrackerMode } from './options';
import { Range } from './Range';
import AttitudeBand from './space/AttitudeBand';
import CommsBand from './space/CommsBand';
import DayNightBand from './space/DayNightBand';
import OrbitNumberBand from './space/OrbitNumberBand';
import SaaBand from './space/SaaBand';
import { default as baseTheme } from './theme/baseTheme';
import { default as darkTheme } from './theme/darkTheme';
import * as utils from './utils';
import VDom from './VDom';

export default class Timeline {

  containerEl: HTMLElement;
  contributions: any[] = [];
  private contributionsById: { [key: string]: any } = {};

  theme: any;
  style: any;

  secondsPerDivision: number;
  loadStart: Date;
  loadStop: Date;

  /**
   * Experimental feature. When true the DOM size will be reduced
   * for bands that don't need to be interactive. This will be done
   * by rendering these bands to an SVG image representing a single
   * DOM node.
   */
  domReduction: boolean;

  /**
   * In which directions the timeline can be panned
   */
  pannable: PanMode;

  /**
   * Original visible start when this component instance was first
   * rendered.
   *
   * It is exposed because it is the most reliable way to for plugins
   * to (re)position elements during mouseover effects.
   * (when panning, ctx.translation only changes upon mouse release)
   */
  unpannedVisibleStart: Date;

  selectedRange?: Range;

  private plugins: { [key: string]: any } = {};
  private themes: { [key: string]: any } = {};

  private eventListeners: any /*TimelineEventHandlers*/ = {
    loadRange: [],
    eventClick: [],
    eventContextMenu: [],
    eventChanged: [],
    eventMouseEnter: [],
    eventMouseMove: [],
    eventMouseLeave: [],
    grabStart: [],
    grabEnd: [],
    rangeSelectionChanged: [],
    sidebarClick: [],
    sidebarContextMenu: [],
    sidebarMouseEnter: [],
    sidebarMouseLeave: [],
    viewportHover: [],
    viewportChange: [],
    viewportChanged: [],
    viewportWheel: [],
  };

  private sidebarWidth = 200;

  private measurerSvg: SVGElement;

  // action-type -> id
  private actionTargetsByType: { [key: string]: string[] } = {};

  private _style: any;
  private tracker: TrackerMode;
  private nodata: boolean;

  private wallclock: boolean;
  private lastManualWallclockTime: Date;

  private data: any;
  private vdom: VDom;

  /**
   * Zoom step (the higher, the more zoomed in)
   */
  private zoom: number;

  /**
   * Available screen space of the client
   */
  private width: number;

  private eventHandling: EventHandling;

  constructor(el: HTMLElement, opts: TimelineOptions) {
    this.containerEl = el;

    this.registerPlugin(EventBand);
    this.registerPlugin(SpacerBand);
    this.registerPlugin(LocationTracker);
    this.registerPlugin(NoDataZone);
    this.registerPlugin(HorizontalSelection);
    this.registerPlugin(WallclockLocator);
    this.registerPlugin(Timescale);

    this.registerPlugin(AttitudeBand);
    this.registerPlugin(CommsBand);
    this.registerPlugin(DayNightBand);
    this.registerPlugin(OrbitNumberBand);
    this.registerPlugin(SaaBand);

    this.registerTheme(baseTheme);
    this.registerTheme(darkTheme);

    // Invisible SVG used to measure font metrics before rendering actual timeline svg
    this.measurerSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    this.measurerSvg.setAttribute('height', '0');
    this.measurerSvg.setAttribute('width', '0');
    this.measurerSvg.setAttribute('style', 'visibility: hidden');
    this.containerEl.appendChild(this.measurerSvg);

    // Target width (incl. sidebar)
    // Not exposed as an option, because user should size using containerEl
    this.width = utils.getWidth(this.containerEl);

    opts = utils.mergeDeep({
      theme: 'base',
      zoom: Timeline.defaultZoom,  // The higher, the more zoomed in
      initialDate: new Date(),
      domReduction: false,
      pannable: 'XY',
      tracker: true,
      nodata: true,
      wallclock: true,
      data: [],
    }, opts);
    this.updateOptions(opts, false /* no draw */);
  }

  /**
   * Registers a plugin with this module.
   */
  private registerPlugin(plugin: any) {
    this.plugins[plugin['type']] = plugin;
  }

  /**
   * Registers a theme with this module.
   */
  private registerTheme(theme: any) {
    this.themes[theme['type']] = {
      type: theme['type'],
      rules: utils.mergeDeep({}, baseTheme['rules'], theme['rules']),
      filters: baseTheme['filters'].concat(theme['filters'] || []),
      defs: baseTheme['defs'].concat(theme['defs'] || []),
    };
  }

  static get minZoom(): number {
    return 1;
  }

  static get defaultZoom(): number {
    return 12;
  }

  static get maxZoom(): number {
    return 14;
  }

  getFontMetrics(textString: string, textSize: number) {
    const el = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    el.setAttribute('font-size', String(textSize));
    el.appendChild(document.createTextNode(textString));
    this.measurerSvg.appendChild(el);
    const bbox = el.getBBox();
    this.measurerSvg.removeChild(el);
    return { height: bbox.height, width: bbox.width };
  }

  updateOptions(opts: TimelineOptions, redraw = true) {
    // Some options will update these values
    let visibleStart;
    let visibleStop;

    if (opts.style) {
      this._style = opts.style;
    }
    if (opts.theme) {
      this.theme = this.themes[opts.theme];
    }
    this.style = utils.mergeDeep({}, this.theme['rules'], this._style);
    this.measurerSvg.setAttribute('font-family', this.style['fontFamily']);

    if (opts.zoom) {
      this.zoom = Math.min(Timeline.maxZoom, Math.max(Timeline.minZoom, opts.zoom));
      this.secondsPerDivision = this.calculateSecondsPerDivision();
    }
    if (opts.initialDate) {
      if (this.eventHandling) {
        this.eventHandling.resetPan();
      }
      const centeredDate = utils.toDate(opts.initialDate);
      this.unpannedVisibleStart = this.leftifyDate(centeredDate);
      visibleStart = this.unpannedVisibleStart;
      const offsetSeconds = this.visibleWidth * (this.secondsPerDivision / this.style['divisionWidth']);
      visibleStop = utils.addSeconds(visibleStart, offsetSeconds);
    }
    if (opts.tracker !== undefined) {
      this.tracker = opts.tracker;
    }
    if (opts.nodata !== undefined) {
      this.nodata = !!opts.nodata;
    }
    if (opts.wallclock !== undefined) {
      this.wallclock = !!opts.wallclock;
    }
    if (opts.data !== undefined) {
      this.data = opts.data;
    }
    if (opts.domReduction !== undefined) {
      this.domReduction = !!opts.domReduction;
    }
    if (opts.sidebarWidth !== undefined) {
      this.sidebarWidth = opts.sidebarWidth;
    }
    if (opts.pannable !== undefined) {
      if (opts.pannable === false) {
        this.pannable = 'NONE';
      } else if (opts.pannable === true) {
        this.pannable = 'XY';
      } else {
        this.pannable = opts.pannable;
      }
    }

    if (!visibleStart) {
      visibleStart = new Date(this.visibleStart.getTime());
    }
    if (!visibleStop) {
      visibleStop = new Date(this.visibleStop.getTime());
    }

    // Make actual load-range a bit wider. We want a comfortable panning
    // experience without re-rendering or loading data all the time.
    const timeBetween = 1 * utils.millisBetween(visibleStart, visibleStop);
    this.loadStart = utils.addMillis(visibleStart, -timeBetween);
    this.loadStop = utils.addMillis(visibleStop, timeBetween);
    if (redraw) {
      // Provide hook to client code to load data in this same range
      this.fireEvent('loadRange', new LoadRangeEvent(this.loadStart, this.loadStop));
      this.doRender(); // Explicitly pass start, to ignore any svg pan information
    }
    return this;
  }

  setData(data: any, redraw = true) {
    this.data = data;
    if (redraw) {
      this.doRender();
    }
    return this;
  }

  on<K extends keyof TimelineEventMap>(evt: K, fn: ((ev: TimelineEventMap[K]) => any)): Timeline {
    if (!(evt in this.eventListeners)) {
      throw new Error(`Unknown event '${evt}'`);
    }
    this.eventListeners[evt].push(fn);
    return this;
  }

  off<K extends keyof TimelineEventMap>(evt: K, fn: ((ev: TimelineEventMap[K]) => any)): Timeline {
    if (!(evt in this.eventListeners)) {
      throw new Error(`Unknown event '${evt}'`);
    }
    this.eventListeners[evt] = this.eventListeners[evt].filter((el: any) => (el !== fn));
    return this;
  }

  fireEvent<K extends keyof TimelineEventMap>(type: K, event: TimelineEventMap[K]) {
    const handlers = this.eventListeners[type];
    handlers.forEach((l: any) => l(event));
  }

  /**
   * Returns the leftmost user-visible date
   */
  get visibleStart() {
    if (this.eventHandling) {
      const xTranslate = this.eventHandling.translation.x;
      const secondsBetween = this.secondsPerDivision * (-xTranslate / this.style['divisionWidth']);
      return utils.addSeconds(this.unpannedVisibleStart, secondsBetween);
    } else {
      return this.unpannedVisibleStart;
    }
  }

  /**
   * Returns the date in the center of the visible non-sidebar view
   */
  get visibleCenter() {
    const offsetSeconds = (this.visibleWidth / 2) * (this.secondsPerDivision / this.style['divisionWidth']);
    return utils.addSeconds(this.visibleStart, offsetSeconds);
  }

  /**
   * Returns the rightmost user-visible date
   */
  get visibleStop() {
    const offsetSeconds = this.visibleWidth * (this.secondsPerDivision / this.style['divisionWidth']);
    return utils.addSeconds(this.visibleStart, offsetSeconds);
  }

  getZoom() {
    return this.zoom;
  }

  zoomIn(x?: number) {
    this.applyZoom(this.zoom + 1, x);
    return this;
  }

  zoomOut(x?: number) {
    this.applyZoom(this.zoom - 1, x);
    return this;
  }

  applyZoom(zoom: number, x?: number) {
    if (x === undefined) {
      const visibleCenter = this.visibleCenter;
      this.zoom = Math.min(Timeline.maxZoom, Math.max(Timeline.minZoom, zoom));
      this.secondsPerDivision = this.calculateSecondsPerDivision();
      this.eventHandling.resetPan();
      this.reveal(visibleCenter);
    } else {
      const xDate = this.toDate(x);
      const xPercent = (x + this.xTranslation) / this.visibleWidth;

      // Update secondsPerDivision
      this.zoom = Math.min(Timeline.maxZoom, Math.max(Timeline.minZoom, zoom));
      this.secondsPerDivision = this.calculateSecondsPerDivision();
      const newBoxSeconds = this.visibleWidth * (this.secondsPerDivision / this.style['divisionWidth']);

      // Derive date for new visibleCenter (at xPercent 0.5)
      const offsetSeconds = newBoxSeconds * (0.5 - xPercent);
      const newCenter = utils.addSeconds(xDate, offsetSeconds);

      this.eventHandling.resetPan();
      this.reveal(newCenter);
    }
    return this;
  }

  get xTranslation(): number {
    if (this.eventHandling) {
      return this.eventHandling.translation.x;
    } else {
      return 0;
    }
  }

  /**
   * Returns the number of seconds within a divisionWidth for the given
   * zoom levels. This is not directly exposed to the client, to control
   * the look of the timescale better.
   */
  private calculateSecondsPerDivision(): number {
    switch (this.zoom) {
      case 1:
        return 1638400;
      case 2:
        return 819200;
      case 3:
        return 409600;
      case 4:
        return 204800;
      case 5:
        return 102400;
      case 6:
        return 51200;
      case 7:
        return 25600;
      case 8:
        return 12800;
      case 9:
        return 6400;
      case 10:
        return 3200;
      case 11:
        return 1600;
      case 12:
        return 800;
      case 13:
        return 400;
      case 14:
        return 200;
      default:
        throw new Error(`Unexpected zoom level ${this.zoom}`);
    }
  }

  /**
   * Returns a date matching the provided x offset
   */
  toDate(offsetX: number) {
    const seconds = (offsetX / this.style['divisionWidth']) * this.secondsPerDivision;
    return utils.addSeconds(this.unpannedVisibleStart, seconds);
  }

  /**
   * Returns the x position in svg points for the given date
   */
  positionDate(date: Date) {
    return this.pointsBetween(this.unpannedVisibleStart, date) + this.xTranslation;
  }

  /**
   * Returns the svg point width between two dates. The sign
   * will be negative if date2 comes after date1.
   */
  pointsBetween(date1: Date, date2: Date) {
    const td = utils.millisBetween(date1, date2) / 1000;
    return (td / this.secondsPerDivision) * this.style.divisionWidth;
  }

  /**
   * Register a target that will respond to user interaction
   */
  registerActionTarget(actionType: ActionType, id: string) {
    if (this.actionTargetsByType.hasOwnProperty(actionType)) {
      this.actionTargetsByType[actionType].push(id);
    } else {
      this.actionTargetsByType[actionType] = [id];
    }
  }

  handleUserAction(id: string, action: Action) {
    for (const contribution of this.contributions) {
      contribution.onAction(id, action);
    }
  }

  /**
   * Manually update the time of the wallclock vertical time locator.
   */
  setWallclockTime(time: string | Date) {
    this.lastManualWallclockTime = utils.toDate(time);
    const contribution = this.contributionsById['_wallclock'];
    contribution.updateTime(this.lastManualWallclockTime);
  }

  get visibleWidth(): number {
    return this.width - this.sidebarWidth;
  }

  getSidebarWidth() {
    return this.sidebarWidth;
  }

  setSidebarWidth(sidebarWidth: number) {
    this.sidebarWidth = sidebarWidth;
  }

  /**
   * Returns a date obtained by converting the specified centered date
   * to the corresponding leftmost visible date.
   */
  private leftifyDate(date: Date): Date {
    const offsetSeconds = - (this.visibleWidth / 2) * (this.secondsPerDivision / this.style.divisionWidth);
    return utils.addSeconds(date, offsetSeconds);
  }

  /**
   * Renders the timeline with the same options as before.
   * Currently this also initiates a 'loadRange' callback, but we should change that someday.
   * For now, use renderWithoutLoadRangeCallback()
   */
  render(): Timeline {
    this.updateOptions({});
    return this;
  }

  /**
   * Rebuilds the DOM structure. This keeps all contribution classes in place
   */
  rebuildDOM(): Timeline {
    // this.updateOptions({}, false)
    // this.doRender()
    this.vdom.rebuild(this.eventHandling.translation);
    return this;
  }

  /**
   * Reinitializes the viewport and gives all the components the chance
   * to contribute to the timeline.
   */
  private doRender() {

    // Provide the opportunity to clean-up resources if this is a re-rendering
    if (this.vdom) {
      for (const contribution of this.contributions) {
        contribution.tearDown();
      }
      this.contributions = [];
      this.contributionsById = {};
    }

    if (Array.isArray(this.data)) {
      for (const spec of this.data) {
        this.createContribution(spec);
      }
    } else {
      for (const spec of this.data['header']) {
        this.createContribution(spec, true);
      }
      for (const spec of this.data['body']) {
        this.createContribution(spec);
      }
    }

    this.createContribution({ type: 'HorizontalSelection' });

    if (this.tracker) {
      this.createContribution({ type: 'LocationTracker' });
    }

    if (this.nodata) {
      this.createContribution({ type: 'NoDataZone' });
    }

    this.createContribution({
      id: '_wallclock',
      type: 'WallclockLocator',
      auto: !!this.wallclock,
      time: this.lastManualWallclockTime || new Date(),
    });

    if (!this.vdom) {
      this.vdom = new VDom(this, this.style);
      this.eventHandling = new EventHandling(this, this.vdom);
      this.containerEl.appendChild(this.vdom.rootEl);
    }
    const ctx = this.vdom.rebuild(this.eventHandling.translation);

    // Provide the opportunity to contributions to initialize
    for (const contribution of this.contributions) {
      contribution.postRender(ctx, this.vdom.rootEl);
    }

    this.fireEvent('viewportChanged', new ViewportChangedEvent());
  }

  private createContribution(spec: any, header = false) {
    const pluginClass = this.plugins[spec['type']];
    if (!pluginClass) {
      throw new Error(`No plugin could be found matching type '${spec['type']}'`);
    }

    // Contribution-specific style is an ordered merge of:
    // 1. Timeline-global style (theme-sensitive)
    // 2. Plugin style (base-rules)
    // 3. Plugin style (theme-sensitive)
    // 4. Contribution style
    const style = utils.mergeDeep({}, this.style);
    if (pluginClass['rules']) {
      utils.mergeDeep(style, pluginClass['rules']);
      if (pluginClass['rules'][this.theme['type']]) {
        utils.mergeDeep(style, pluginClass['rules'][this.theme['type']]);
      }
    }
    if (spec['style']) {
      utils.mergeDeep(style, spec['style']);
    }

    const contribution = new pluginClass(this, spec /* opts */, style);
    contribution['type'] = pluginClass['type'];
    contribution['header'] = header;
    this.contributions.push(contribution);

    if (spec['id']) {
      this.contributionsById[spec['id']] = contribution;
    }
  }

  selectRange(start: Date | string, stop: Date | string) {
    const startDate = utils.toDate(start);
    const stopDate = utils.toDate(stop);
    if (startDate.getTime() <= stopDate.getTime()) {
      this.selectedRange = { start: startDate, stop: stopDate };
    } else {
      this.selectedRange = { start: stopDate, stop: startDate };
    }

    for (const contribution of this.contributions) {
      if (contribution.type === HorizontalSelection.type) {
        contribution.setSelection(this.selectedRange);
        this.fireEvent('rangeSelectionChanged', new RangeSelectionChangedEvent(this.selectedRange));
        return;
      }
    }
  }

  clearSelection() {
    this.selectedRange = undefined;
    for (const contribution of this.contributions) {
      if (contribution.type === HorizontalSelection.type) {
        contribution.clearSelection();
        this.fireEvent('rangeSelectionChanged', new RangeSelectionChangedEvent());
        return;
      }
    }
  }

  /**
   * Returns the action target of the given element, but only if it was registered
   * with the specified type.
   */
  findActionTarget(element: Element, actionType?: ActionType) {
    const targetElement = this.getTargetElement(element);
    if (!actionType) {
      return targetElement;
    } else if (targetElement) {
      const validTargets = this.actionTargetsByType[actionType];
      if (validTargets && validTargets.indexOf(targetElement.id) >= 0) {
        return targetElement;
      }
    }
  }

  /**
   * Returns the parent element that bears the group id and is passed to
   * the user in events.
   */
  private getTargetElement(element: Element) {
    let el = element;
    while (el && el.parentElement && (el !== this.vdom.rootEl) && !el['id']) {
      el = el.parentElement;
    }
    if (el && el['id']) {
      return el;
    }
  }

  setRootCursor(cursorStyle: string, important = false) {
    if (important) {
      this.vdom.rootEl.style.setProperty('cursor', cursorStyle, 'important');
    } else {
      this.vdom.rootEl.style.setProperty('cursor', cursorStyle);
    }
  }

  resetRootCursor() {
    this.vdom.rootEl.style.removeProperty('cursor');
  }

  /**
   * Forwards the viewport by a fraction of the viewport size.
   */
  goForward(x = 0.3) {
    const pos = this.positionDate(this.visibleCenter);
    this.reveal(this.toDate(pos + (x * (this.visibleWidth))));
  }

  /**
   * Reverses the viewport by a fraction of the viewport size.
   */
  goBackward(x = 0.3) {
    const pos = this.positionDate(this.visibleCenter);
    this.reveal(this.toDate(pos - (x * (this.visibleWidth))));
  }

  /**
   * Centers the view on the specified date
   */
  reveal(date: Date) {
    this.updateOptions({
      initialDate: date,
    });
    return this;
  }
}
