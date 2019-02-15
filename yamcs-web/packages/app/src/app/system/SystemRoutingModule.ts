import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayControlServicesGuard } from '../core/guards/MayControlServicesGuard';
import { MayReadTablesGuard } from '../core/guards/MayReadTablesGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { JvmPage } from './jvm/JvmPage';
import { ServicesPage } from './services/ServicesPage';
import { StreamColumnsTab } from './stream/StreamColumnsTab';
import { StreamDataTab } from './stream/StreamDataTab';
import { StreamPage } from './stream/StreamPage';
import { StreamScriptTab } from './stream/StreamScriptTab';
import { StreamsPage } from './streams/StreamsPage';
import { TableDataTab } from './table/TableDataTab';
import { TableInfoTab } from './table/TableInfoTab';
import { TablePage } from './table/TablePage';
import { TableScriptTab } from './table/TableScriptTab';
import { TablesPage } from './tables/TablesPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, InstanceExistsGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',  // See DisplaysPage.ts for documentation
    component: InstancePage,
    children: [
      {
        path: 'tables',
        pathMatch: 'full',
        component: TablesPage,
        canActivate: [MayReadTablesGuard],
      },
      {
        path: 'tables/:name',
        component: TablePage,
        canActivate: [MayReadTablesGuard],
        canActivateChild: [MayReadTablesGuard],
        children: [
          {
            path: '',
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
      },
      {
        path: 'streams',
        component: StreamsPage,
        pathMatch: 'full',
      },
      {
        path: 'streams/:name',
        component: StreamPage,
        children: [
          {
            path: '',
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
      },
      {
        path: 'services',
        component: ServicesPage,
        canActivate: [MayControlServicesGuard],
      },
      {
        path: 'jvm',
        component: JvmPage,
        canActivate: [AuthGuard],
      },
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class SystemRoutingModule { }

export const routingComponents = [
  JvmPage,
  ServicesPage,
  StreamsPage,
  StreamPage,
  StreamColumnsTab,
  StreamDataTab,
  StreamScriptTab,
  TablesPage,
  TablePage,
  TableInfoTab,
  TableDataTab,
  TableScriptTab,
];
