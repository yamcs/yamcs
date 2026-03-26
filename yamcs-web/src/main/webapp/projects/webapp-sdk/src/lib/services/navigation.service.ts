import { Injectable } from '@angular/core';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class NavigationService {
  constructor(private router: Router) {}

  /**
   * Triggers a full component reload by bouncing through the RouteRefreshComponent.
   *
   * @param context The new context (optional).
   */
  refreshCurrentRoute(context?: string): void {
    this.router.navigate(['/route-refresh'], {
      skipLocationChange: true,
      state: {
        targetUrl: this.router.url,
        context: context,
      },
    });
  }
}
