import { Injectable, OnDestroy, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppearanceService } from './appearance.service';
import { AuthService } from './auth.service';
import { YamcsService } from './yamcs.service';

const YA_ACTIVATED_ROUTE = 'YA_ACTIVATED_ROUTE';

/**
 * Provides access to services from the main webapp.
 *
 * This service is available in webcomponents, so it
 * can be safely used in shared components.
 */
@Injectable({ providedIn: 'root' })
export class SdkBridge implements EventListenerObject, OnDestroy {
  /**
   * The main webapp router
   */
  router: Router;

  /**
   * The main webapp appearance service
   */
  appearanceService: AppearanceService;

  /**
   * The main webapp Yamcs service
   */
  yamcs: YamcsService;

  /**
   * The main webapp auth service
   */
  authService: AuthService;

  /**
   * Route data for the activated route
   */
  routeData = signal<Map<string, any>>(new Map());

  constructor() {
    window.addEventListener(YA_ACTIVATED_ROUTE, this);
  }

  handleEvent(event: Event): void {
    if (event.type === YA_ACTIVATED_ROUTE) {
      const customEvent = event as CustomEvent;
      if (customEvent.detail.route) {
        let child = customEvent.detail.route;
        while (child.firstChild) {
          child = child.firstChild;
        }

        const data = child.snapshot.data;
        const m = new Map<string, any>(Object.entries(data));
        this.routeData.set(m);
      }
    }
  }

  ngOnDestroy(): void {
    window.removeEventListener(YA_ACTIVATED_ROUTE, this);
  }
}
