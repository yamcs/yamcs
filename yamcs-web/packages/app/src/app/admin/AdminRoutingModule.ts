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
import { EndpointsPage } from './endpoints/EndpointsPage';
import { AdminHomePage } from './home/AdminHomePage';
import { CreateGroupPage } from './iam/CreateGroupPage';
import { CreateServiceAccountPage } from './iam/CreateServiceAccountPage';
import { CreateUserPage } from './iam/CreateUserPage';
import { EditGroupPage } from './iam/EditGroupPage';
import { EditUserPage } from './iam/EditUserPage';
import { GroupPage } from './iam/GroupPage';
import { GroupsPage } from './iam/GroupsPage';
import { ServiceAccountsPage } from './iam/ServiceAccountsPage';
import { UserPage } from './iam/UserPage';
import { UsersPage } from './iam/UsersPage';
import { LeapSecondsPage } from './leap-seconds/LeapSecondsPage';
import { PluginsPage } from './plugins/PluginsPage';
import { ServicesPage } from './services/ServicesPage';

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
        path: 'endpoints',
        component: EndpointsPage,
      },
      {
        path: 'leap-seconds',
        component: LeapSecondsPage,
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
        path: 'iam/service-accounts',
        pathMatch: 'full',
        component: ServiceAccountsPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'iam/service-accounts/create',
        pathMatch: 'full',
        component: CreateServiceAccountPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'iam/users',
        pathMatch: 'full',
        component: UsersPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'iam/users/create',
        pathMatch: 'full',
        component: CreateUserPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'iam/users/:username',
        pathMatch: 'full',
        component: UserPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'iam/users/:username/edit',
        pathMatch: 'full',
        component: EditUserPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'iam/groups',
        pathMatch: 'full',
        component: GroupsPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'iam/groups/create',
        pathMatch: 'full',
        component: CreateGroupPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'iam/groups/:name',
        pathMatch: 'full',
        component: GroupPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'iam/groups/:name/edit',
        pathMatch: 'full',
        component: EditGroupPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
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
  BucketsPage,
  BucketPage,
  BucketPlaceholderPage,
  ClientsPage,
  CreateGroupPage,
  CreateServiceAccountPage,
  CreateUserPage,
  EditGroupPage,
  EditUserPage,
  EndpointsPage,
  GroupsPage,
  GroupPage,
  LeapSecondsPage,
  PluginsPage,
  ServiceAccountsPage,
  ServicesPage,
  UsersPage,
  UserPage,
];
