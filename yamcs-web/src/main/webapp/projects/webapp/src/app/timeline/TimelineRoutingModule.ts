import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { BandsPage } from './BandsPage';
import { CreateBandPage } from './CreateBandPage';
import { CreateViewPage } from './CreateViewPage';
import { EditBandPage } from './EditBandPage';
import { EditItemPage } from './EditItemPage';
import { EditViewPage } from './EditViewPage';
import { ItemsPage } from './ItemsPage';
import { TimelineChartPage } from './TimelineChartPage';
import { ViewsPage } from './ViewsPage';
import { CreateCommandBandPage } from './commandBand/CreateCommandBandPage';
import { CreateItemBandPage } from './itemBand/CreateItemBandPage';
import { CreateSpacerPage } from './spacer/CreateSpacerPage';
import { CreateTimeRulerPage } from './timeRuler/CreateTimeRulerPage';

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
        path: 'bands/create/command-band',
        pathMatch: 'full',
        component: CreateCommandBandPage,
      }, {
        path: 'bands/:band',
        pathMatch: 'full',
        component: EditBandPage,
      }, {
        path: 'items',
        pathMatch: 'full',
        component: ItemsPage,
      }, {
        path: 'items/:item',
        pathMatch: 'full',
        component: EditItemPage,
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
  CreateCommandBandPage,
  CreateItemBandPage,
  CreateSpacerPage,
  CreateTimeRulerPage,
  CreateViewPage,
  EditBandPage,
  EditItemPage,
  EditViewPage,
  ItemsPage,
  TimelineChartPage,
  ViewsPage,
];
