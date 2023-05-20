import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { AlgorithmPage } from './AlgorithmPage';
import { AlgorithmSummaryTab } from './AlgorithmSummaryTab';
import { AlgorithmTraceTab } from './AlgorithmTraceTab';
import { AlgorithmsPage } from './AlgorithmsPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    component: InstancePage,
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: AlgorithmsPage,
      }, {
        path: ':qualifiedName',
        component: AlgorithmPage,
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'summary',
          }, {
            path: 'summary',
            component: AlgorithmSummaryTab,
          }, {
            path: 'trace',
            component: AlgorithmTraceTab,
          }
        ]
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AlgorithmsRoutingModule { }

export const routingComponents = [
  AlgorithmsPage,
  AlgorithmPage,
  AlgorithmSummaryTab,
  AlgorithmTraceTab,
];
