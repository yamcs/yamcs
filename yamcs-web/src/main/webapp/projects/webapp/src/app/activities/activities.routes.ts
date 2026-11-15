import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { ActivityDetailsTabComponent } from './activity-details-tab/activity-details-tab.component';
import { ActivityListComponent } from './activity-list/activity-list.component';
import { ActivityLogTabComponent } from './activity-log-tab/activity-log-tab.component';
import { ActivityComponent } from './activity/activity.component';
import { FiringListComponent } from './triggers/firing-list/firing-list.component';
import { mayReadTriggersGuardFn } from './triggers/shared/triggers';
import { TriggerEditComponent } from './triggers/trigger-edit/trigger-edit.component';
import { TriggerListComponent } from './triggers/trigger-list/trigger-list.component';

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
        path: 'triggers',
        canActivate: [mayReadTriggersGuardFn],
        children: [
          {
            path: '',
            pathMatch: 'full',
            component: TriggerListComponent,
          },
          {
            path: 'create',
            component: TriggerEditComponent,
          },
          {
            path: ':trigger',
            component: TriggerEditComponent,
          },
        ],
      },
      {
        path: 'trigger-firings',
        canActivate: [mayReadTriggersGuardFn],
        component: FiringListComponent,
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
