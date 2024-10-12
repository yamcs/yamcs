import { Injectable } from '@angular/core';
import { Router } from '@angular/router';

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
}
