import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { BandsPage } from './BandsPage';
import { CreateBandPage } from './CreateBandPage';
import { CreateViewPage } from './CreateViewPage';
import { EditBandPage } from './EditBandPage';
import { EditViewPage } from './EditViewPage';
import { CreateItemBandPage } from './itemBand/CreateItemBandPage';
import { ItemsPage } from './ItemsPage';
import { CreateSpacerPage } from './spacer/CreateSpacerPage';
import { TimelineChartPage } from './TimelineChartPage';
import { CreateTimeRulerPage } from './timeRuler/CreateTimeRulerPage';
import { ViewsPage } from './ViewsPage';

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
        path: 'views',
        pathMatch: 'full',
        component: ViewsPage,
      }, {
        path: 'views/create',
        pathMatch: 'full',
        component: CreateViewPage,
      }, {
        path: 'views/:view',
        pathMatch: 'full',
        component: EditViewPage,
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
      }, {
        path: 'items',
        pathMatch: 'full',
        component: ItemsPage,
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
  CreateViewPage,
  EditBandPage,
  EditViewPage,
  ItemsPage,
  TimelineChartPage,
  ViewsPage,
];
