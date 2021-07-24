import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { BandsPage } from './BandsPage';
import { CreateBandPage } from './CreateBandPage';
import { TimelineChartPage } from './TimelineChartPage';

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
        redirectTo: 'chart',
      }, {
        path: 'chart',
        component: TimelineChartPage,
      }, {
        path: 'bands',
        pathMatch: 'full',
        component: BandsPage,
      }, {
        path: 'bands/create',
        pathMatch: 'full',
        component: CreateBandPage,
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class TimelineRoutingModule { }

export const routingComponents = [
  BandsPage,
  CreateBandPage,
  TimelineChartPage,
];
