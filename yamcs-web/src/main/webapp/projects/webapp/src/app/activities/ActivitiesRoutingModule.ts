import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { ActivitiesPage } from './ActivitiesPage';
import { ActivityDetailsTab } from './ActivityDetailsTab';
import { ActivityLogTab } from './ActivityLogTab';
import { ActivityPage } from './ActivityPage';

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
        component: ActivitiesPage,
      }, {
        path: ':activityId',
        component: ActivityPage,
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'log',
          }, {
            path: 'log',
            component: ActivityLogTab,
          }, {
            path: 'details',
            component: ActivityDetailsTab,
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
export class ActivitiesRoutingModule { }

export const routingComponents = [
  ActivitiesPage,
  ActivityLogTab,
  ActivityDetailsTab,
  ActivityPage,
];
