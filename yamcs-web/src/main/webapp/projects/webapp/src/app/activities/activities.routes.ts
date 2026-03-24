import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { ActivityDetailsTabComponent } from './activity-details-tab/activity-details-tab.component';
import { ActivityListComponent } from './activity-list/activity-list.component';
import { ActivityLogTabComponent } from './activity-log-tab/activity-log-tab.component';
import { ActivityComponent } from './activity/activity.component';

export const ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: ActivityListComponent,
      },
      {
        path: ':activityId',
        component: ActivityComponent,
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'log',
          },
          {
            path: 'log',
            component: ActivityLogTabComponent,
          },
          {
            path: 'details',
            component: ActivityDetailsTabComponent,
          },
        ],
      },
    ],
  },
];
