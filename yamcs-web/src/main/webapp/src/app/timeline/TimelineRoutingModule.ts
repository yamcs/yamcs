import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { BandsPage } from './BandsPage';
import { CreateBandPage } from './CreateBandPage';
import { EditBandPage } from './EditBandPage';
import { CreateItemBandPage } from './itemBand/CreateItemBandPage';
import { CreateSpacerPage } from './spacer/CreateSpacerPage';
import { TimelineChartPage } from './TimelineChartPage';
import { CreateTimeRulerPage } from './timeRuler/CreateTimeRulerPage';

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
      }, {
        path: 'bands/create/item-band',
        pathMatch: 'full',
        component: CreateItemBandPage,
      }, {
        path: 'bands/create/spacer',
        pathMatch: 'full',
        component: CreateSpacerPage,
      }, {
        path: 'bands/create/time-ruler',
        pathMatch: 'full',
        component: CreateTimeRulerPage,
      }, {
        path: 'bands/:band',
        pathMatch: 'full',
        component: EditBandPage,
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
  CreateItemBandPage,
  CreateSpacerPage,
  CreateTimeRulerPage,
  EditBandPage,
  TimelineChartPage,
];
