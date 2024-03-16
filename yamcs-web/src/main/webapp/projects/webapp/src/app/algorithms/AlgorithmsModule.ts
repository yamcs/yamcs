import { NgModule } from '@angular/core';
import { RouterModule, Routes, UrlMatcher, UrlSegment } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { SharedModule } from '../shared/SharedModule';
import { InstancePage } from '../shared/template/InstancePage';
import { AlgorithmDetail } from './AlgorithmDetail';
import { AlgorithmPage } from './AlgorithmPage';
import { AlgorithmStatusComponent } from './AlgorithmStatusComponent';
import { AlgorithmSummaryTab } from './AlgorithmSummaryTab';
import { AlgorithmTraceTab } from './AlgorithmTraceTab';
import { AlgorithmsPage } from './AlgorithmsPage';

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
    component: AlgorithmsPage,
  }, {
    matcher: algorithmMatcher,
    component: AlgorithmPage,
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: '-/summary',
    }, {
      path: '-/summary',
      component: AlgorithmSummaryTab,
    }, {
      path: '-/trace',
      component: AlgorithmTraceTab,
    }]
  }]
}];

@NgModule({
  imports: [
    SharedModule,
    RouterModule.forChild(routes),
  ],
  declarations: [
    AlgorithmDetail,
    AlgorithmPage,
    AlgorithmsPage,
    AlgorithmStatusComponent,
    AlgorithmSummaryTab,
    AlgorithmTraceTab,
  ],
})
export class AlgorithmsModule {
}
