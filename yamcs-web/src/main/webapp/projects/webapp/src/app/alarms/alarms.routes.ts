import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { ActionLogTabComponent } from './action-log-tab/action-log-tab.component';
import { ActiveAlarmListComponent } from './active-alarm-list/active-alarm-list.component';
import { AlarmHistoryComponent } from './alarm-history/alarm-history.component';
import { PendingAlarmListComponent } from './pending-alarm-list/pending-alarm-list.component';

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
        component: ActiveAlarmListComponent,
      },
      {
        path: 'pending',
        component: PendingAlarmListComponent,
      },
      {
        path: 'history',
        component: AlarmHistoryComponent,
      },
      {
        path: 'log',
        component: ActionLogTabComponent,
      },
    ],
  },
];
