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
import { CreateGroupPage } from './users/CreateGroupPage';
import { CreateUserPage } from './users/CreateUserPage';
import { EditUserPage } from './users/EditUserPage';
import { GroupPage } from './users/GroupPage';
import { GroupsPage } from './users/GroupsPage';
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
        path: 'user-management/users',
        pathMatch: 'full',
        component: UsersPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'user-management/users/create',
        pathMatch: 'full',
        component: CreateUserPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'user-management/users/:username',
        pathMatch: 'full',
        component: UserPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'user-management/users/:username/edit',
        pathMatch: 'full',
        component: EditUserPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'user-management/groups',
        pathMatch: 'full',
        component: GroupsPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'user-management/groups/new',
        pathMatch: 'full',
        component: CreateGroupPage,
        canActivate: [AuthGuard, UnselectInstanceGuard],
      },
      {
        path: 'user-management/groups/:name',
        component: GroupPage,
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
  CreateUserPage,
  EditUserPage,
  GroupsPage,
  GroupPage,
  PluginsPage,
  ServicesPage,
  UsersPage,
  UserPage,
];
