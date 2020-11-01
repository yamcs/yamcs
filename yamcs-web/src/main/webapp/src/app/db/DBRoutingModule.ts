import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DatabasePage } from '../db/database/DatabasePage';
import { DatabaseStreamsTab } from '../db/database/DatabaseStreamsTab';
import { DatabaseTablesTab } from '../db/database/DatabaseTablesTab';
import { StreamColumnsTab } from '../db/database/stream/StreamColumnsTab';
import { StreamDataTab } from '../db/database/stream/StreamDataTab';
import { StreamPage } from '../db/database/stream/StreamPage';
import { StreamScriptTab } from '../db/database/stream/StreamScriptTab';
import { TableDataTab } from '../db/database/table/TableDataTab';
import { TableInfoTab } from '../db/database/table/TableInfoTab';
import { TablePage } from '../db/database/table/TablePage';
import { TableScriptTab } from '../db/database/table/TableScriptTab';
import { DatabasesPage } from '../db/databases/DatabasesPage';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'databases'
  }, {
    path: 'databases',
    pathMatch: 'full',
    component: DatabasesPage,
  }, {
    path: 'databases/:database',
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
