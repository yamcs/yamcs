import { Routes } from '@angular/router';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { clearContextGuardFn } from '../core/guards/ClearContextGuard';
import { mayAccessAdminAreaGuardFn } from '../core/guards/MayAccessAdminAreaGuard';
import { mayControlAccessGuardFn } from '../core/guards/MayControlAccessGuard';
import { mayControlArchivingGuardFn } from '../core/guards/MayControlArchivingGuard';
import { mayControlServicesGuardFn } from '../core/guards/MayControlServicesGuard';
import { mayReadSystemInfoGuardFn } from '../core/guards/MayReadSystemInfoGuard';
import { AdminActionLogComponent } from './admin-action-log/admin-action-log.component';
import { DatabaseListComponent } from './databases/database-list/database-list.component';
import { DatabaseComponent } from './databases/database/database.component';
import { ShellTabComponent } from './databases/shell-tab/shell-tab.component';
import { StreamColumnListComponent } from './databases/stream-column-list/stream-column-list.component';
import { StreamDataTabComponent } from './databases/stream-data-tab/stream-data-tab.component';
import { StreamListComponent } from './databases/stream-list/stream-list.component';
import { StreamScriptTabComponent } from './databases/stream-script-tab/stream-script-tab.component';
import { StreamComponent } from './databases/stream/stream.component';
import { TableDataTabComponent } from './databases/table-data-tab/table-data-tab.component';
import { TableInfoTabComponent } from './databases/table-info-tab/table-info-tab.component';
import { TableListComponent } from './databases/table-list/table-list.component';
import { TableScriptTabComponent } from './databases/table-script-tab/table-script-tab.component';
import { TableComponent } from './databases/table/table.component';
import { HttpTrafficComponent } from './http-traffic/http-traffic.component';
import { CreateGroupComponent } from './iam/create-group/create-group.component';
import { CreateServiceAccountComponent } from './iam/create-service-account/create-service-account.component';
import { CreateUserComponent } from './iam/create-user/create-user.component';
import { EditGroupComponent } from './iam/edit-group/edit-group.component';
import { EditUserComponent } from './iam/edit-user/edit-user.component';
import { GroupListComponent } from './iam/group-list/group-list.component';
import { GroupComponent } from './iam/group/group.component';
import { RoleListComponent } from './iam/role-list/role-list.component';
import { RoleComponent } from './iam/role/role.component';
import { ServiceAccountListComponent } from './iam/service-account-list/service-account-list.component';
import { UserListComponent } from './iam/user-list/user-list.component';
import { UserComponent } from './iam/user/user.component';
import { LeapSecondsComponent } from './leap-seconds/leap-seconds.component';
import { ProcessorTypesComponent } from './processor-types/processor-types.component';
import { ReplicationComponent } from './replication/replication/replication.component';
import { RocksDbDatabasesComponent } from './rocksdb/rocksdb-database-list/rocksdb-database-list.component';
import { RocksDbDatabaseComponent } from './rocksdb/rocksdb-database/rocksdb-database.component';
import { RouteListComponent } from './routes/route-list/route-list.component';
import { ServiceListComponent } from './services/service-list/service-list.component';
import { SessionListComponent } from './sessions/session-list.component';
import { AdminPageComponent } from './shared/admin-page/admin-page.component';
import { SystemComponent } from './system/system.component';
import { ThreadListComponent } from './threads/thread-list/thread-list.component';
import { ThreadComponent } from './threads/thread/thread.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, clearContextGuardFn, mayAccessAdminAreaGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: AdminPageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    component: AdminActionLogComponent,
  }, {
    path: 'http-traffic',
    canActivate: [mayReadSystemInfoGuardFn],
    component: HttpTrafficComponent,
  }, {
    path: 'sessions',
    canActivate: [mayControlAccessGuardFn],
    component: SessionListComponent,
  }, {
    path: 'routes',
    canActivate: [mayReadSystemInfoGuardFn],
    component: RouteListComponent,
  }, {
    path: 'leap-seconds',
    canActivate: [mayReadSystemInfoGuardFn],
    component: LeapSecondsComponent,
  }, {
    path: 'processor-types',
    canActivate: [mayReadSystemInfoGuardFn],
    component: ProcessorTypesComponent,
  }, {
    path: 'replication',
    canActivate: [mayReadSystemInfoGuardFn],
    component: ReplicationComponent,
  }, {
    path: 'services',
    canActivate: [mayControlServicesGuardFn],
    component: ServiceListComponent,
  }, {
    path: 'databases',
    canActivate: [mayControlArchivingGuardFn],
    children: [{
      path: '',
      pathMatch: 'full',
      component: DatabaseListComponent,
    }, {
      path: ':database',
      component: DatabaseComponent,
      children: [{
        path: '',
        pathMatch: 'full',
        redirectTo: 'tables',
      }, {
        path: 'tables',
        pathMatch: 'full',
        component: TableListComponent,
      }, {
        path: 'tables/:table',
        component: TableComponent,
        children: [{
          path: '',
          pathMatch: 'full',
          redirectTo: 'info',
        }, {
          path: 'info',
          component: TableInfoTabComponent,
        }, {
          path: 'data',
          component: TableDataTabComponent,
        }, {
          path: 'script',
          component: TableScriptTabComponent,
        }],
      }, {
        path: 'shell',
        pathMatch: 'full',
        component: ShellTabComponent,
      }, {
        path: 'streams',
        pathMatch: 'full',
        component: StreamListComponent,
      }, {
        path: 'streams/:stream',
        component: StreamComponent,
        children: [{
          path: '',
          pathMatch: 'full',
          redirectTo: 'columns',
        }, {
          path: 'columns',
          component: StreamColumnListComponent,
        }, {
          path: 'data',
          component: StreamDataTabComponent,
        }, {
          path: 'script',
          component: StreamScriptTabComponent,
        }],
      }]
    }]
  }, {
    path: 'rocksdb',
    runGuardsAndResolvers: 'always',
    canActivate: [mayControlArchivingGuardFn],
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: 'databases'
    }, {
      path: 'databases',
      pathMatch: 'full',
      component: RocksDbDatabasesComponent,
    }, {
      path: 'databases/:tablespace',
      children: [{
        path: '**',
        component: RocksDbDatabaseComponent,
      }]
    }],
  }, {
    path: 'iam/service-accounts',
    pathMatch: 'full',
    component: ServiceAccountListComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/service-accounts/create',
    pathMatch: 'full',
    component: CreateServiceAccountComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/users',
    pathMatch: 'full',
    component: UserListComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/users/create',
    pathMatch: 'full',
    component: CreateUserComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/users/:username',
    pathMatch: 'full',
    component: UserComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/users/:username/edit',
    pathMatch: 'full',
    component: EditUserComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/groups',
    pathMatch: 'full',
    component: GroupListComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/groups/create',
    pathMatch: 'full',
    component: CreateGroupComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/groups/:name',
    pathMatch: 'full',
    component: GroupComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/groups/:name/edit',
    pathMatch: 'full',
    component: EditGroupComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/roles',
    pathMatch: 'full',
    component: RoleListComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'iam/roles/:name',
    pathMatch: 'full',
    component: RoleComponent,
    canActivate: [mayControlAccessGuardFn],
  }, {
    path: 'threads',
    pathMatch: 'full',
    component: ThreadListComponent,
    canActivate: [mayReadSystemInfoGuardFn],
  }, {
    path: 'threads/:id',
    component: ThreadComponent,
    canActivate: [mayReadSystemInfoGuardFn],
  }, {
    path: 'system',
    pathMatch: 'full',
    component: SystemComponent,
    canActivate: [mayReadSystemInfoGuardFn],
  }]
}];
