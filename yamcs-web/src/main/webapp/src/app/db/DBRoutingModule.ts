import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DatabasePage } from './database/DatabasePage';
import { DatabaseShellTab } from './database/shell/DatabaseShellTab';
import { StreamColumnsTab } from './database/stream/StreamColumnsTab';
import { StreamDataTab } from './database/stream/StreamDataTab';
import { StreamPage } from './database/stream/StreamPage';
import { StreamScriptTab } from './database/stream/StreamScriptTab';
import { DatabaseStreamsTab } from './database/streams/DatabaseStreamsTab';
import { TableDataTab } from './database/table/TableDataTab';
import { TableInfoTab } from './database/table/TableInfoTab';
import { TablePage } from './database/table/TablePage';
import { TableScriptTab } from './database/table/TableScriptTab';
import { DatabaseTablesTab } from './database/tables/DatabaseTablesTab';
import { DatabasesPage } from './databases/DatabasesPage';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    component: DatabasesPage,
  }, {
    path: ':database',
    component: DatabasePage,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'tables',
      }, {
        path: 'tables',
        pathMatch: 'full',
        component: DatabaseTablesTab,
      }, {
        path: 'tables/:table',
        component: TablePage,
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'info',
          }, {
            path: 'info',
            component: TableInfoTab,
          }, {
            path: 'data',
            component: TableDataTab,
          }, {
            path: 'script',
            component: TableScriptTab,
          }
        ],
      }, {
        path: 'shell',
        pathMatch: 'full',
        component: DatabaseShellTab,
      }, {
        path: 'streams',
        pathMatch: 'full',
        component: DatabaseStreamsTab,
      }, {
        path: 'streams/:stream',
        component: StreamPage,
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'columns',
          }, {
            path: 'columns',
            component: StreamColumnsTab,
          }, {
            path: 'data',
            component: StreamDataTab,
          }, {
            path: 'script',
            component: StreamScriptTab,
          }
        ],
      }]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DBRoutingModule { }

export const routingComponents = [
  DatabasesPage,
  DatabasePage,
  DatabaseShellTab,
  DatabaseStreamsTab,
  DatabaseTablesTab,
  StreamPage,
  StreamColumnsTab,
  StreamDataTab,
  StreamScriptTab,
  TablePage,
  TableInfoTab,
  TableDataTab,
  TableScriptTab,
];
