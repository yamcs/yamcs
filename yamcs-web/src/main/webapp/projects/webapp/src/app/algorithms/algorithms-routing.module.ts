import { NgModule } from '@angular/core';
import { RouterModule, Routes, UrlMatcher, UrlSegment } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
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

const routes: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePage,
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

@NgModule({
  imports: [
    RouterModule.forChild(routes),
  ],
})
export class AlgorithmsRoutingModule {
}
