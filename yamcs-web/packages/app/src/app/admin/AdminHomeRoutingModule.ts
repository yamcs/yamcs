import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { SuperuserGuard } from '../core/guards/SuperuserGuard';
import { UnselectInstanceGuard } from '../core/guards/UnselectInstanceGuard';
import { AdminPage } from './AdminPage';
import { GlobalBucketsPage } from './buckets/GlobalBucketsPage';
import { ClientsPage } from './clients/ClientsPage';
import { AdminHomePage } from './home/AdminHomePage';
import { GlobalServicesPage } from './services/GlobalServicesPage';

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
        component: GlobalBucketsPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'clients',
        component: ClientsPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'services',
        component: GlobalServicesPage,
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
  GlobalBucketsPage,
  GlobalServicesPage,
];
