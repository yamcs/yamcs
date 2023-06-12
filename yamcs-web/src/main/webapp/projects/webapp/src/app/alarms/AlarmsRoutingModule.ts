import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { ActionLogTab } from './ActionLogTab';
import { AlarmHistoryPage } from './AlarmHistoryPage';
import { AlarmsPage } from './AlarmsPage';

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
        component: AlarmsPage,
      }, {
        path: 'history',
        component: AlarmHistoryPage,
      }, {
        path: 'log',
        component: ActionLogTab,
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AlarmsRoutingModule { }

export const routingComponents = [
  AlarmHistoryPage,
  AlarmsPage,
  ActionLogTab,
];
