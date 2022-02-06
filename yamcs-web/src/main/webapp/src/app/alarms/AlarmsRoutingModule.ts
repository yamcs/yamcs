import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { ActionLogTab } from './ActionLogTab';
import { AlarmsPage } from './AlarmsPage';
import { ClosedAlarmsPage } from './ClosedAlarmsPage';

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
        component: AlarmsPage,
      }, {
        path: 'closed',
        component: ClosedAlarmsPage,
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
  AlarmsPage,
  ActionLogTab,
  ClosedAlarmsPage,
];
