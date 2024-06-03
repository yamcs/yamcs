import { Routes, UrlMatcher, UrlSegment } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { AlgorithmListComponent } from './algorithm-list/algorithm-list.component';
import { AlgorithmSummaryTabComponent } from './algorithm-summary-tab/algorithm-summary-tab.component';
import { AlgorithmTraceTabComponent } from './algorithm-trace-tab/algorithm-trace-tab.component';
import { AlgorithmComponent } from './algorithm/algorithm.component';

const algorithmMatcher: UrlMatcher = url => {
  let consumed = url;

  // Stop consuming at /-/
  // (handled by Angular again)
  const idx = url.findIndex(segment => segment.path === '-');
  if (idx !== -1) {
    consumed = url.slice(0, idx);
  }

  const algorithm = '/' + consumed.map(segment => segment.path).join('/');
  return {
    consumed,
    posParams: {
      'algorithm': new UrlSegment(algorithm, {}),
    },
  };
};

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    component: AlgorithmListComponent,
  }, {
    matcher: algorithmMatcher,
    component: AlgorithmComponent,
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: '-/summary',
    }, {
      path: '-/summary',
      component: AlgorithmSummaryTabComponent,
    }, {
      path: '-/trace',
      component: AlgorithmTraceTabComponent,
    }]
  }]
}];
