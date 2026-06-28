import { inject, Service } from '@angular/core';
import { Router } from '@angular/router';

@Service()
export class NavigationService {
  private router = inject(Router);

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
