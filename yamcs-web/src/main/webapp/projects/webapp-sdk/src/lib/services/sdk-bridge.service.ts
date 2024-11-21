import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { AppearanceService } from './appearance.service';

/**
 * Provides access to services from the main webapp.
 *
 * This service is available in webcomponents, so it
 * can be safely used in shared components.
 */
@Injectable({ providedIn: 'root' })
export class SdkBridge {

  /**
   * The main webapp router
   */
  router: Router;

  /**
   * The main webapp appearance service
   */
  appearanceService: AppearanceService;
}
