import Point from './Point';
import Timeline from './Timeline';
import VDom from './VDom';
import { GrabEndEvent, GrabStartEvent, ViewportChangeEvent, ViewportChangedEvent, ViewportHoverEvent, WheelViewportEvent } from './events';

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
  private grabTarget?: Element;
  private grabStart?: Point;

  /**
   * High-level element currently under mouse. Do not use for grab
   * handling because the mouse may enter different targets while
   * in the middle of grabbing another.
   */
  private mouseEnteredTarget?: Element;

  /**
   * Minimum number of points before doing a pan. Helps get rid of minor movements, especially
   * when just clicking around. Try to keep it low, cause it creates a slight stutter on first
   * movement.
   */
  private snap = 5;

  // Global handlers instantiated only during a grab action.
  // Purpose is to support the user doing grab actions while leaving the timeline.
  private documentMouseMoveListener = (e: MouseEvent) => this.onDocumentMouseMove(e);
  private documentMouseUpListener = (e: MouseEvent) => this.onDocumentMouseUp(e);
  private documentMouseLeaveListener = (e: any) => this.onDocumentMouseLeave(e);

  /**
   * Swallows any click event wherever they may originate.
   * Usually there's 0 or 1 when the user ends the grab,
   * depending on where the mouse is released.
   */
  private clickBlocker = (e: MouseEvent) => {
    // Remove ourself. This to prevent capturing unrelated events.
    document.removeEventListener('click', this.clickBlocker, true /* Must be same as when created */);

    e.preventDefault();
    e.stopPropagation();
    return false;
  }

  constructor(private timeline: Timeline, private vdom: VDom) {
    vdom.rootEl.addEventListener('click', e => this.onClick(e), false);
    vdom.rootEl.addEventListener('contextmenu', e => this.onContextMenu(e), false);
    vdom.rootEl.addEventListener('mousedown', e => this.onMouseDown(e), false);
    vdom.rootEl.addEventListener('mouseup', e => this.onMouseUp(e), false);
    vdom.rootEl.addEventListener('mousemove', e => this.onMouseMove(e), false);
    vdom.rootEl.addEventListener('wheel', e => this.onWheel(e), false);

    vdom.rootEl.addEventListener('touchstart', e => this.onTouchStart(e as TouchEvent), false);
    vdom.rootEl.addEventListener('touchmove', e => this.onTouchMove(e as TouchEvent), false);
    vdom.rootEl.addEventListener('touchend', e => this.onTouchEnd(e as TouchEvent), false);
  }

  private onClick(event: MouseEvent) {
    if (!this.interactionAllowed) {
      return;
    }
    if (!this.grabbing && this.mouseEnteredTarget) {
      const actionTarget = this.timeline.findActionTarget(event.target as Element, 'click');
      if (actionTarget) {
        this.timeline.handleUserAction(this.mouseEnteredTarget.id, {
          type: 'click',
          target: actionTarget,
          clientX: event.clientX,
          clientY: event.clientY,
          grabbing: this.grabbing,
        });
        return;
      }
    }

    this.timeline.clearSelection();
  }

  private onContextMenu(event: any) {
    if (!this.interactionAllowed) {
      return;
    }
    if (this.mouseEnteredTarget) {
      const actionTarget = this.timeline.findActionTarget(event.target as Element, 'contextmenu');
      if (actionTarget) {
        this.timeline.handleUserAction(this.mouseEnteredTarget.id, {
          type: 'contextmenu',
          target: actionTarget,
          clientX: event.clientX,
          clientY: event.clientY,
          grabbing: this.grabbing,
        });
      }
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
    document.removeEventListener('click', this.clickBlocker, true /* Must be same as when created */);
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

  private prepareGrab(transformedGrabStart: Point, event: TouchEvent | MouseEvent) {

    // This one is only used to measure snap
    this.mouseDownStart = transformedGrabStart;

    // This is the source of the translation (which therefore must first negate
    // the pre-existing transform)
    this.grabStart = this.mouseDownStart.minus(this.translation);

    this.grabTarget = this.timeline.findActionTarget(event.target as Element, 'grabstart');
    this.panning = !this.grabTarget;
  }

  /**
   * Performs a grab operation, but only if the mouse has moved
   * far enough from the mousedown location.
   */
  private maybeInitializeGrab(dst: Point, clientX: number, clientY: number) {
    if (!this.grabbing && Math.abs(this.mouseDownStart!.distanceTo(dst)) > this.snap) {
      // These listeners are added to the document so that they keep triggering
      // when the mouse moves outside the viewport.
      document.addEventListener('click', this.clickBlocker, true /* capture ! */);
      document.addEventListener('mousemove', this.documentMouseMoveListener);
      document.addEventListener('mouseup', this.documentMouseUpListener);
      document.addEventListener('mouseleave', this.documentMouseLeaveListener);

      this.timeline.fireEvent('grabStart', new GrabStartEvent());

      // Output grabstart (but not on pan)
      if (this.grabTarget && !this.grabbing) {
        const x = this.mouseDownStart!.x - this.translation.x - this.timeline.getSidebarWidth();
        const date = this.timeline.toDate(x);
        this.timeline.handleUserAction(this.grabTarget!.id, {
          type: 'grabstart',
          target: this.grabTarget,
          clientX,
          clientY,
          date,
          grabbing: true,
          grabStart: this.untranslatePoint(this.mouseDownStart),
          grabPosition: this.untranslatePoint(dst),
        });
      }
      this.grabbing = true;
    }
  }

  private untranslatePoint(point?: Point) {
    if (point && this.translation) {
      return point.minus(this.translation);
    } else {
      return point;
    }
  }

  private onMouseMove(event: MouseEvent) {
    const pos = this.mousePosition(event);
    if (this.interactionAllowed && this.mouseDownStart) {
      this.maybeInitializeGrab(pos, event.clientX, event.clientY);
    }

    // Signal hover event
    const hoverX = pos.x - this.translation.x - this.timeline.getSidebarWidth();
    const hoverDate = this.timeline.toDate(hoverX);
    if (hoverDate >= this.timeline.visibleStart) {
      this.timeline.fireEvent('viewportHover', new ViewportHoverEvent(hoverDate, hoverX));
    } else {
      this.timeline.fireEvent('viewportHover', new ViewportHoverEvent());
    }

    // High-level interactions with registered events
    const actionTarget = this.timeline.findActionTarget(event.target as Element);
    if (actionTarget) {
      if (actionTarget !== this.mouseEnteredTarget) {
        if (this.mouseEnteredTarget) {
          this.timeline.handleUserAction(this.mouseEnteredTarget.id, {
            type: 'mouseleave',
            target: actionTarget,
            clientX: event.clientX,
            clientY: event.clientY,
            grabbing: this.grabbing,
          });
        }
        this.mouseEnteredTarget = actionTarget;
        this.timeline.handleUserAction(actionTarget.id, {
          type: 'mouseenter',
          target: actionTarget,
          clientX: event.clientX,
          clientY: event.clientY,
          grabbing: this.grabbing,
        });
      }
      this.timeline.handleUserAction(actionTarget.id, {
        type: 'mousemove',
        target: actionTarget,
        clientX: event.clientX,
        clientY: event.clientY,
        grabbing: this.grabbing,
      });
    } else if (this.mouseEnteredTarget) {
      this.timeline.handleUserAction(this.mouseEnteredTarget.id, {
        type: 'mouseleave',
        target: actionTarget,
        clientX: event.clientX,
        clientY: event.clientY,
        grabbing: this.grabbing,
      });
      this.mouseEnteredTarget = undefined;
    }
  }

  private onTouchMove(event: TouchEvent) {
    if (this.interactionAllowed && this.mouseDownStart) {
      const touched = event.touches[0];
      this.maybeInitializeGrab(this.touchPosition(event), touched.pageX, touched.pageY);
    }

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private onMouseUp(event: MouseEvent) {
    if (!this.interactionAllowed) {
      return;
    }

    // This is also set in the document-level mouseUp
    // handler, but that one is only active while grabbing.
    this.mouseDownStart = undefined;
  }

  private onTouchEnd(event: TouchEvent) {
    if (!this.interactionAllowed) {
      return;
    }

    const pos = this.touchPosition(event);
    this.endGrab(pos);

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private endGrab(dst?: Point) {
    if (this.grabbing) {
      document.removeEventListener('mousemove', this.documentMouseMoveListener);
      document.removeEventListener('mouseup', this.documentMouseUpListener);
      document.removeEventListener('mouseleave', this.documentMouseLeaveListener);
      this.grabbing = false;
      if (this.panning) {
        this.reloadData();
        this.timeline.fireEvent('viewportChanged', new ViewportChangedEvent());
      } else {
        let x;
        let date;
        if (dst) {
          x = dst!.x - this.translation.x - this.timeline.getSidebarWidth();
          date = this.timeline.toDate(x);
        }
        this.timeline.fireEvent('grabEnd', new GrabEndEvent());
        this.timeline.handleUserAction(this.grabTarget!.id, {
          type: 'grabend',
          target: this.grabTarget,
          clientX: -1,
          clientY: -1,
          date,
          grabbing: false,
          grabStart: this.untranslatePoint(this.mouseDownStart),
          grabPosition: this.untranslatePoint(dst),
        });
      }
    }
    this.mouseDownStart = undefined;
    this.grabTarget = undefined;
  }

  private reloadData() {
    this.interactionAllowed = false;
    this.timeline.setRootCursor('wait', true);

    // Force redraw, we may get close to unrendered regions
    // Async, to allow previous pan to become visible, before reloading
    // This prevents a movement stutter
    setTimeout(() => {
      this.timeline.render();
      this.interactionAllowed = true;
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

  /**
   * Global handler only active during a grab.
   */
  private onDocumentMouseMove(event: MouseEvent) {
    const pos = this.mousePosition(event);

    // Output grabmove if grab target, otherwise just pan
    if (this.grabTarget) {
      const x = pos.x - this.translation.x - this.timeline.getSidebarWidth();
      const date = this.timeline.toDate(x);
      this.timeline.handleUserAction(this.grabTarget!.id, {
        type: 'grabmove',
        target: this.grabTarget,
        clientX: event.clientX,
        clientY: event.clientY,
        date,
        grabbing: true,
        grabStart: this.untranslatePoint(this.mouseDownStart),
        grabPosition: this.untranslatePoint(pos),
      });
    } else {
      this.pan(pos);
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

  private onDocumentMouseUp(event: MouseEvent) {
    if (!this.interactionAllowed) {
      return;
    }

    // Ensure new re-rendering aligns with last viewport
    this.onMouseMove(event);

    const pos = this.mousePosition(event);
    this.endGrab(pos);

    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  private onDocumentMouseLeave(event: Event) {
    this.timeline.fireEvent('viewportHover', new ViewportHoverEvent());

    // Triggers when user moves outside the browser while panning. This does not have an
    // effect on the SVG anyway, but without this code it would create a stutter when
    // mouse returns back, because the mouse could be much further than the rendering had
    // advanced at the point of leaving.
    // TODO no longer needed?
    if (this.mouseDownStart) {
      this.endGrab();

      // TODO hmm double render with above reloadData??
      this.timeline.render(); // Force redraw, we may get close to unrendered regions
    }
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
