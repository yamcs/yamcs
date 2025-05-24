import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Router } from '@angular/router';

/**
 * A utility component used to force a full re-initialization of the current route.
 *
 * By navigating here and back to the previous URL, we trigger a complete
 * destruction and recreation of the component tree. This avoids the need
 * for individual components to manually subscribe to global state changes
 * (e.g., timezone updates or instance/processor switching).
 *
 * This should be called using { skipLocationChange: true } to ensure
 * the URL remains invisible to the user.
 */
@Component({
  selector: 'app-route-refresh',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteRefreshComponent {
  constructor(private router: Router) {
    const navigation = this.router.currentNavigation();
    const state = navigation?.extras.state as {
      targetUrl: string;
      context?: string;
    };

    if (state?.targetUrl) {
      const tree = this.router.parseUrl(state.targetUrl);

      if (state.context) {
        tree.queryParams['c'] = state.context;
      }

      this.router.navigateByUrl(tree);
    } else {
      // Fallback if someone hits the route directly without state
      this.router.navigate(['/']);
    }
  }
}
