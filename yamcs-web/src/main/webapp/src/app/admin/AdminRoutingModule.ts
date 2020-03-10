import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { ClearContextGuard } from '../core/guards/ClearContextGuard';
import { SuperuserGuard } from '../core/guards/SuperuserGuard';
import { AdminPage } from './AdminPage';
import { BucketPage } from './buckets/BucketPage';
import { BucketPlaceholderPage } from './buckets/BucketPlaceHolderPage';
import { BucketsPage } from './buckets/BucketsPage';
import { ConnectionsPage } from './connections/ConnectionsPage';
import { AdminHomePage } from './home/AdminHomePage';
import { CreateGroupPage } from './iam/CreateGroupPage';
import { CreateServiceAccountPage } from './iam/CreateServiceAccountPage';
import { CreateUserPage } from './iam/CreateUserPage';
import { EditGroupPage } from './iam/EditGroupPage';
import { EditUserPage } from './iam/EditUserPage';
import { GroupPage } from './iam/GroupPage';
import { GroupsPage } from './iam/GroupsPage';
import { RolePage } from './iam/RolePage';
import { RolesPage } from './iam/RolesPage';
import { ServiceAccountsPage } from './iam/ServiceAccountsPage';
import { UserPage } from './iam/UserPage';
import { UsersPage } from './iam/UsersPage';
import { LeapSecondsPage } from './leap-seconds/LeapSecondsPage';
import { PluginsPage } from './plugins/PluginsPage';
import { ProcessorTypesPage } from './processor-types/ProcessorTypesPage';
import { DatabasePage } from './rocksdb/DatabasePage';
import { DatabasesPage } from './rocksdb/DatabasesPage';
import { RoutesPage } from './routes/RoutesPage';
import { ServicesPage } from './services/ServicesPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, ClearContextGuard, SuperuserGuard],
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
        path: 'connections',
        component: ConnectionsPage,
      },
      {
        path: 'routes',
        component: RoutesPage,
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
        path: 'processor-types',
        component: ProcessorTypesPage,
      },
      {
        path: 'services',
        component: ServicesPage,
      },
      {
        path: 'rocksdb',
        runGuardsAndResolvers: 'always',
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'databases'
          },
          {
            path: 'databases',
            pathMatch: 'full',
            component: DatabasesPage,
          },
          {
            path: 'databases/:tablespace',
            children: [
              {
                path: '**',
                component: DatabasePage,
              }
            ]
          },
        ],
      },
      {
        path: 'iam/service-accounts',
        pathMatch: 'full',
        component: ServiceAccountsPage,
      },
      {
        path: 'iam/service-accounts/create',
        pathMatch: 'full',
        component: CreateServiceAccountPage,
      },
      {
        path: 'iam/users',
        pathMatch: 'full',
        component: UsersPage,
      },
      {
        path: 'iam/users/create',
        pathMatch: 'full',
        component: CreateUserPage,
      },
      {
        path: 'iam/users/:username',
        pathMatch: 'full',
        component: UserPage,
      },
      {
        path: 'iam/users/:username/edit',
        pathMatch: 'full',
        component: EditUserPage,
      },
      {
        path: 'iam/groups',
        pathMatch: 'full',
        component: GroupsPage,
      },
      {
        path: 'iam/groups/create',
        pathMatch: 'full',
        component: CreateGroupPage,
      },
      {
        path: 'iam/groups/:name',
        pathMatch: 'full',
        component: GroupPage,
      },
      {
        path: 'iam/groups/:name/edit',
        pathMatch: 'full',
        component: EditGroupPage,
      },
      {
        path: 'iam/roles',
        pathMatch: 'full',
        component: RolesPage,
      },
      {
        path: 'iam/roles/:name',
        pathMatch: 'full',
        component: RolePage,
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AdminRoutingModule { }

export const routingComponents = [
  AdminHomePage,
  BucketsPage,
  BucketPage,
  BucketPlaceholderPage,
  ConnectionsPage,
  CreateGroupPage,
  CreateServiceAccountPage,
  CreateUserPage,
  DatabasesPage,
  DatabasePage,
  EditGroupPage,
  EditUserPage,
  GroupsPage,
  GroupPage,
  LeapSecondsPage,
  PluginsPage,
  ProcessorTypesPage,
  RolesPage,
  RolePage,
  RoutesPage,
  ServiceAccountsPage,
  ServicesPage,
  UsersPage,
  UserPage,
];
