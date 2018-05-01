import Timeline from './Timeline';
import Point from './Point';
import VDom from './VDom';
import {
  ViewportChangeEvent,
  ViewportChangedEvent,
  ViewportHoverEvent,
  WheelViewportEvent,
} from './events';

/**
 * Groups global DOM event listeners.
 * Depending on registered action ids, different events are either handled internally (e.g. panning), or
 * offloaded to the plugins that registered those actions.
 */
export default class EventHandling {

  /**
   * Visible translation of this viewport. Initially (0, 0),
   * but may be different when the viewport was panned
   * in either direction.
   */
  public translation = new Point(0, 0);

  private interactionAllowed = true;

  // Only set when actually grabbing (after snap detection)
  private grabbing = false;

  // These grab-related properties are set optimistically (before snap detection)
  private panning = false;
  private mouseDownStart?: Point;
  private grabElement?: Element;
  private grabStart?: Point;

  /**
   * Used to signal from 'mousedown' when the 'click' event
   * should not do anything (i.e. because the user was grabbing)
   */
  private skipNextClick = false;

  /**
   * High-level element currently under mouse. Do not use for grab
   * handling because the mouse may enter different targets while
   * in the middle of grabbing another.
   */
  private mouseEnteredTarget: string | undefined;

  /**
   * Minimum number of points before doing a pan. Helps get rid of minor movements, especially
   * when just clicking around. Try to keep it low, cause it creates a slight stutter on first
   * movement.
   */
  private snap = 5;

  constructor(private timeline: Timeline, private vdom: VDom) {
    const viewport = this;
    vdom.rootEl.addEventListener('click', e => viewport.onClick(e), false);
    vdom.rootEl.addEventListener('contextmenu', e => viewport.onContextMenu(e), false);
    vdom.rootEl.addEventListener('mousedown', e => viewport.onMouseDown(e), false);
    vdom.rootEl.addEventListener('mousemove', e => viewport.onMouseMove(e), false);
    vdom.rootEl.addEventListener('mouseup', e => viewport.onMouseUp(e), false);
    vdom.rootEl.addEventListener('mouseleave', e => viewport.onMouseLeave(e), false);
    vdom.rootEl.addEventListener('wheel', e => viewport.onWheel(e), false);

    vdom.rootEl.addEventListener('touchstart', e => viewport.onTouchStart(e as TouchEvent), false);
    vdom.rootEl.addEventListener('touchmove', e => viewport.onTouchMove(e as TouchEvent), false);
    vdom.rootEl.addEventListener('touchend', e => viewport.onTouchEnd(e as TouchEvent), false);
  }

  private setInteractionAllowed(interactionAllowed: boolean) {
    this.interactionAllowed = interactionAllowed;
  }

  /**
   * Returns the mouse position in coordinates
   */
  private mousePosition(event: MouseEvent): Point {
    const rect = this.vdom.rootEl.getBoundingClientRect();
    let svgPoint = this.vdom.rootEl.createSVGPoint();
    svgPoint.x = event.clientX - rect.left;
    svgPoint.y = event.clientY - rect.top;

    // Would have made sense to use rootEl here, but getCTM() does not work for the root
    // element on FF. And getScreenCTM() leads to incorrect positioning.
    // mousePositionReferenceEl shares the same coordinate system as rootEl.
    svgPoint = svgPoint.matrixTransform(this.vdom.mousePositionReferenceEl.getCTM()!.inverse());
    return new Point(svgPoint.x, svgPoint.y);
  }

  /**
   * Returns the touch position in coordinates
   */
  private touchPosition(event: TouchEvent): Point {
    const rect = this.vdom.rootEl.getBoundingClientRect();
    const touched = event.touches[0];
    let svgPoint = this.vdom.rootEl.createSVGPoint();
    svgPoint.x = touched.pageX - rect.left;
    svgPoint.y = touched.pageY - rect.top;

    // Would have made sense to use rootEl here, but getCTM() does not work for the root
    // element on FF. And getScreenCTM() leads to incorrect positioning.
    // mousePositionReferenceEl shares the same coordinate system as rootEl.
    svgPoint = svgPoint.matrixTransform(this.vdom.mousePositionReferenceEl.getCTM()!.inverse());
    return new Point(svgPoint.x, svgPoint.y);
  }

  private onClick(event: MouseEvent) {
    if (this.skipNextClick) {
      this.skipNextClick = false;
      return;
    }
    if (!this.interactionAllowed) {
      return;
    }
    if (!this.grabbing && this.mouseEnteredTarget) {
      const targetElement = this.timeline.getTargetElement(event.target as Element);
      this.timeline.handleUserAction(this.mouseEnteredTarget, {
        type: 'click',
        target: targetElement,
        clientX: event.clientX,
        clientY: event.clientY,
      });
    }
  }

  private onContextMenu(event: any) {
    if (!this.interactionAllowed) {
      return;
    }
    if (this.mouseEnteredTarget) {
      const targetElement = this.timeline.getTargetElement(event.target as Element);
      this.timeline.handleUserAction(this.mouseEnteredTarget, {
        type: 'contextmenu',
        target: targetElement,
        clientX: event.clientX,
        clientY: event.clientY,
      });
    }

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private onTouchStart(event: TouchEvent) {
    if (!this.interactionAllowed) {
      return;
    }

    this.prepareGrab(this.touchPosition(event), event);

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private onMouseDown(event: MouseEvent) {
    if (!this.interactionAllowed) {
      return;
    }

    if (event.button === 0) {
      this.prepareGrab(this.mousePosition(event), event);
    }

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private prepareGrab(transformedGrabStart: Point, event: TouchEvent | MouseEvent) {

    // This one is only used to measure snap
    this.mouseDownStart = transformedGrabStart;

    // This is the source of the translation (which therefore must first negate
    // the pre-existing transform)
    this.grabStart = this.mouseDownStart.minus(this.translation);

    this.grabElement = this.timeline.getTargetElement(event.target as Element);
    this.panning = !this.grabElement;
  }

  /**
   * Performs a grab operation, but only if the mouse has moved
   * far enough from the mousedown location.
   */
  private maybeGrab(dst: Point, clientX: number, clientY: number) {
    if (Math.abs(this.mouseDownStart!.distanceTo(dst)) > this.snap) {

      // Output grabstart (but not on pan)
      if (this.grabElement && !this.grabbing) {
        this.timeline.handleUserAction(this.grabElement!.id, {
          type: 'grabstart',
          target: this.grabElement,
          clientX,
          clientY,
        });
      }

      this.grabbing = true;
      this.skipNextClick = true;

      // Output grabmove if grab target, otherwise just pan
      if (this.grabElement) {
        this.timeline.handleUserAction(this.grabElement!.id, {
          type: 'grabmove',
          target: this.grabElement,
          clientX,
          clientY,
        });
      } else {
        this.pan(dst);
      }
    }
  }

  /**
   * Adjusts the various sections to the new pan state
   *
   * @param dst position where to pan towards (may be subject to transforms)
   */
  private pan(dst: Point) {
    let pt = dst.minus(this.grabStart!);

    // Allow user-overrides to not support panning
    if (this.timeline.pannable === 'X_ONLY') {
      pt = pt.withY(0);
    } else if (this.timeline.pannable === 'Y_ONLY') {
      pt = pt.withX(0);
    } else if (this.timeline.pannable === 'NONE') {
      pt = new Point(0, 0);
    }

    // Translation is bounded in y-direction, so body does not detach from header
    this.translation = pt.withY(Math.min(pt.y, 0));

    const bodyTransform = this.vdom.rootEl.createSVGTransform();
    bodyTransform.setTranslate(this.translation.x, this.translation.y);
    this.vdom.bodyViewportEl.transform.baseVal.initialize(bodyTransform);

    const headerTransform = this.vdom.rootEl.createSVGTransform();
    headerTransform.setTranslate(this.translation.x, 0);
    this.vdom.headerViewportEl.transform.baseVal.initialize(headerTransform);

    const sidebarTransform = this.vdom.rootEl.createSVGTransform();
    sidebarTransform.setTranslate(0, this.translation.y);
    this.vdom.bodySidebarEl.transform.baseVal.initialize(sidebarTransform);

    const overlayTransform = this.vdom.rootEl.createSVGTransform();
    overlayTransform.setTranslate(this.translation.x, this.translation.y);
    this.vdom.overlayViewportEl.transform.baseVal.initialize(overlayTransform);

    const xOverlayTransform = this.vdom.rootEl.createSVGTransform();
    xOverlayTransform.setTranslate(this.translation.x, 0);
    this.vdom.xOverlayViewportEl.transform.baseVal.initialize(xOverlayTransform);

    const yOverlayTransform = this.vdom.rootEl.createSVGTransform();
    yOverlayTransform.setTranslate(0, this.translation.y);
    this.vdom.yOverlayViewportEl.transform.baseVal.initialize(yOverlayTransform);

    this.timeline.fireEvent('viewportChange', new ViewportChangeEvent());
  }

  private onMouseMove(event: MouseEvent) {
    const pos = this.mousePosition(event);
    if (this.interactionAllowed && this.mouseDownStart) {
      this.maybeGrab(pos, event.clientX, event.clientY);
    }

    // Signal hover event
    const hoverX = pos.x - this.translation.x - this.timeline.style['sidebarWidth'];
    const hoverDate = this.timeline.toDate(hoverX);
    if (hoverDate >= this.timeline.visibleStart) {
      this.timeline.fireEvent('viewportHover', new ViewportHoverEvent(hoverDate, hoverX));
    } else {
      this.timeline.fireEvent('viewportHover', new ViewportHoverEvent());
    }

    // High-level interactions with registered events
    const targetElement = this.timeline.getTargetElement(event.target as Element);
    if (targetElement && this.timeline.isActionTarget(targetElement['id'])) {
      if (targetElement['id'] !== this.mouseEnteredTarget) {
        if (this.mouseEnteredTarget) {
          this.timeline.handleUserAction(this.mouseEnteredTarget, {
            type: 'mouseleave',
            target: targetElement,
            clientX: event.clientX,
            clientY: event.clientY,
          });
        }
        this.mouseEnteredTarget = targetElement['id'];
        this.timeline.handleUserAction(targetElement['id'], {
          type: 'mouseenter',
          target: targetElement,
          clientX: event.clientX,
          clientY: event.clientY,
        });
      }
      this.timeline.handleUserAction(targetElement['id'], {
        type: 'mousemove',
        target: targetElement,
        clientX: event.clientX,
        clientY: event.clientY,
      });
    } else if (!targetElement && this.mouseEnteredTarget) {
      this.timeline.handleUserAction(this.mouseEnteredTarget, {
        type: 'mouseleave',
        target: targetElement,
        clientX: event.clientX,
        clientY: event.clientY,
      });
      this.mouseEnteredTarget = undefined;
    }

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private onTouchMove(event: TouchEvent) {
    if (this.interactionAllowed && this.mouseDownStart) {
      const touched = event.touches[0];
      this.maybeGrab(this.touchPosition(event), touched.pageX, touched.pageY);
    }

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private onMouseUp(event: MouseEvent) {
    if (!this.interactionAllowed) {
      return;
    }

    // Ensure new re-rendering aligns with last viewport
    this.onMouseMove(event);

    this.endGrab();

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private onTouchEnd(event: TouchEvent) {
    if (!this.interactionAllowed) {
      return;
    }

    this.endGrab();

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private endGrab() {
    if (this.grabbing) {
      this.grabbing = false;
      if (this.panning) {
        this.reloadData();
        this.timeline.fireEvent('viewportChanged', new ViewportChangedEvent());
      } else {
        this.timeline.handleUserAction(this.grabElement!.id, {
          type: 'grabend',
          target: this.grabElement,
          clientX: -1,
          clientY: -1,
        });
      }
    }
    this.mouseDownStart = undefined;
    this.grabElement = undefined;
  }

  private onMouseLeave(event: Event) {
    this.timeline.fireEvent('viewportHover', new ViewportHoverEvent());

    // Triggers when user moves outside the browser while panning. This does not have an
    // effect on the SVG anyway, but without this code it would create a stutter when
    // mouse returns back, because the mouse could be much further than the rendering had
    // advanced at the point of leaving.
    if (this.mouseDownStart) {
      this.endGrab();

      // TODO hmm double render with above reloadData??
      this.timeline.render(); // Force redraw, we may get close to unrendered regions
    }
  }

  private reloadData() {
    this.setInteractionAllowed(false);
    this.timeline.setRootCursor('wait', true);

    // Force redraw, we may get close to unrendered regions
    // Async, to allow previous pan to become visible, before reloading
    // This prevents a movement stutter
    setTimeout(() => {
      this.timeline.render();
      this.setInteractionAllowed(true);
      this.timeline.resetRootCursor();
    }, 50);
  }

  /**
   * Use wheel delta to determine zoom in/out
   */
  private onWheel(event: WheelEvent) {

    // Mouse location x is fixated while zooming
    const mouseX = this.mousePosition(event).x;
    const hoverX = mouseX - this.translation.x;
    if (event.wheelDelta > 0) {
      this.timeline.zoomIn(hoverX);
    } else if (event.wheelDelta < 0) {
      this.timeline.zoomOut(hoverX);
    }

    // Cascade further
    const evt = new WheelViewportEvent(event);
    this.timeline.fireEvent('viewportWheel', evt);
  }

  resetPan() {
    const bodyTransform = this.vdom.rootEl.createSVGTransform();
    bodyTransform.setTranslate(0, 0);
    this.vdom.bodyViewportEl.transform.baseVal.initialize(bodyTransform);

    const headerTransform = this.vdom.rootEl.createSVGTransform();
    headerTransform.setTranslate(0, 0);
    this.vdom.headerViewportEl.transform.baseVal.initialize(headerTransform);

    const sidebarTransform = this.vdom.rootEl.createSVGTransform();
    sidebarTransform.setTranslate(0, 0);
    this.vdom.bodySidebarEl.transform.baseVal.initialize(sidebarTransform);

    const underlayTransform = this.vdom.rootEl.createSVGTransform();
    underlayTransform.setTranslate(0, 0);
    this.vdom.underlayViewportEl.transform.baseVal.initialize(underlayTransform);

    const xUnderlayTransform = this.vdom.rootEl.createSVGTransform();
    xUnderlayTransform.setTranslate(0, 0);
    this.vdom.xUnderlayViewportEl.transform.baseVal.initialize(xUnderlayTransform);

    const yUnderlayTransform = this.vdom.rootEl.createSVGTransform();
    yUnderlayTransform.setTranslate(0, 0);
    this.vdom.yUnderlayViewportEl.transform.baseVal.initialize(yUnderlayTransform);

    const overlayTransform = this.vdom.rootEl.createSVGTransform();
    overlayTransform.setTranslate(0, 0);
    this.vdom.overlayViewportEl.transform.baseVal.initialize(overlayTransform);

    const xOverlayTransform = this.vdom.rootEl.createSVGTransform();
    xOverlayTransform.setTranslate(0, 0);
    this.vdom.xOverlayViewportEl.transform.baseVal.initialize(xOverlayTransform);

    const yOverlayTransform = this.vdom.rootEl.createSVGTransform();
    yOverlayTransform.setTranslate(0, 0);
    this.vdom.yOverlayViewportEl.transform.baseVal.initialize(yOverlayTransform);

    this.translation = new Point(0, 0);
  }
}
