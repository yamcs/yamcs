import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { SuperuserGuard } from '../core/guards/SuperuserGuard';
import { UnselectInstanceGuard } from '../core/guards/UnselectInstanceGuard';
import { AdminPage } from '../shared/template/AdminPage';
import { BucketPage } from './buckets/BucketPage';
import { BucketPlaceholderPage } from './buckets/BucketPlaceHolderPage';
import { BucketsPage } from './buckets/BucketsPage';
import { ClientsPage } from './clients/ClientsPage';
import { AdminHomePage } from './home/AdminHomePage';
import { PluginsPage } from './plugins/PluginsPage';
import { ServicesPage } from './services/ServicesPage';
import { UserPage } from './users/UserPage';
import { UsersPage } from './users/UsersPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, UnselectInstanceGuard, SuperuserGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',
    component: AdminPage,
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: AdminHomePage,
      },
      {
        path: 'buckets',
        pathMatch: 'full',
        component: BucketsPage,
      },
      {
        path: 'buckets/:instance/:name',
        component: BucketPlaceholderPage,
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
      },
      {
        path: 'plugins',
        component: PluginsPage,
      },
      {
        path: 'services',
        component: ServicesPage,
      },
      {
        path: 'rocksdb',
        loadChildren: () => import('src/app/rocksdb/RocksDbModule').then(m => m.RocksDbModule),
      },
      {
        path: 'users',
        pathMatch: 'full',
        component: UsersPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'users/:username',
        component: UserPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      }
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class AdminRoutingModule { }

export const routingComponents = [
  AdminHomePage,
  ClientsPage,
  BucketsPage,
  BucketPage,
  BucketPlaceholderPage,
  PluginsPage,
  ServicesPage,
  UsersPage,
  UserPage,
];
