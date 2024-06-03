import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { BandListComponent } from './band-list/band-list.component';
import { CreateCommandBandComponent } from './command-band/create-command-band/create-command-band.component';
import { CreateBandComponent } from './create-band/create-band.component';
import { CreateViewComponent } from './create-view/create-view.component';
import { EditBandComponent } from './edit-band/edit-band.component';
import { EditItemComponent } from './edit-item/edit-item.component';
import { EditViewComponent } from './edit-view/edit-view.component';
import { CreateItemBandComponent } from './item-band/create-item-band/create-item-band.component';
import { ItemListComponent } from './item-list/item-list.component';
import { CreateSpacerComponent } from './spacer/create-spacer/create-spacer.component';
import { CreateTimeRulerComponent } from './time-ruler/create-time-ruler/create-time-ruler.component';
import { TimelineChartComponent } from './timeline-chart/timeline-chart.component';
import { ViewListComponent } from './view-list/view-list.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    redirectTo: 'chart',
  }, {
    path: 'chart',
    component: TimelineChartComponent,
  }, {
    path: 'views',
    pathMatch: 'full',
    component: ViewListComponent,
  }, {
    path: 'views/create',
    pathMatch: 'full',
    component: CreateViewComponent,
  }, {
    path: 'views/:view',
    pathMatch: 'full',
    component: EditViewComponent,
  }, {
    path: 'bands',
    pathMatch: 'full',
    component: BandListComponent,
  }, {
    path: 'bands/create',
    pathMatch: 'full',
    component: CreateBandComponent,
  }, {
    path: 'bands/create/item-band',
    pathMatch: 'full',
    component: CreateItemBandComponent,
  }, {
    path: 'bands/create/spacer',
    pathMatch: 'full',
    component: CreateSpacerComponent,
  }, {
    path: 'bands/create/time-ruler',
    pathMatch: 'full',
    component: CreateTimeRulerComponent,
  }, {
    path: 'bands/create/command-band',
    pathMatch: 'full',
    component: CreateCommandBandComponent,
  }, {
    path: 'bands/:band',
    pathMatch: 'full',
    component: EditBandComponent,
  }, {
    path: 'items',
    pathMatch: 'full',
    component: ItemListComponent,
  }, {
    path: 'items/:item',
    pathMatch: 'full',
    component: EditItemComponent,
  }]
}];
