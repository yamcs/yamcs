import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { SuperuserGuard } from '../core/guards/SuperuserGuard';
import { UnselectInstanceGuard } from '../core/guards/UnselectInstanceGuard';
import { AdminPage } from './AdminPage';
import { BucketPage } from './buckets/BucketPage';
import { BucketPlaceholderPage } from './buckets/BucketPlaceHolderPage';
import { BucketsPage } from './buckets/BucketsPage';
import { ClientsPage } from './clients/ClientsPage';
import { AdminHomePage } from './home/AdminHomePage';
import { ServicesPage } from './services/ServicesPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, SuperuserGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',
    component: AdminPage,
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: AdminHomePage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'buckets',
        pathMatch: 'full',
        component: BucketsPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'buckets/:instance/:name',
        component: BucketPlaceholderPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
        children: [
          {
            path: '**',
            component: BucketPage,
          }
        ],
      },
      {
        path: 'clients',
        component: ClientsPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'services',
        component: ServicesPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class AdminHomeRoutingModule { }

export const routingComponents = [
  AdminHomePage,
  ClientsPage,
  BucketsPage,
  BucketPage,
  BucketPlaceholderPage,
  ServicesPage,
];
