import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { AlgorithmPage } from './AlgorithmPage';
import { AlgorithmsPage } from './AlgorithmsPage';
import { AlgorithmSummaryTab } from './AlgorithmSummaryTab';
import { AlgorithmTraceTab } from './AlgorithmTraceTab';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, AttachContextGuard],
    canActivateChild: [AuthGuard],
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
