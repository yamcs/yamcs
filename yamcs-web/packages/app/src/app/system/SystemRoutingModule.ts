import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayControlServicesGuard } from '../core/guards/MayControlServicesGuard';
import { MayReadTablesGuard } from '../core/guards/MayReadTablesGuard';
import { ClientsPage } from './clients/ClientsPage';
import { DashboardPage } from './dashboard/DashboardPage';
import { LinksPage } from './links/LinksPage';
import { ProcessorPage } from './processors/ProcessorPage';
import { ProcessorTCTab } from './processors/ProcessorTCTab';
import { ProcessorTMTab } from './processors/ProcessorTMTab';
import { ProcessorsPage } from './processors/ProcessorsPage';
import { ServicesPage } from './services/ServicesPage';
import { StreamColumnsTab } from './stream/StreamColumnsTab';
import { StreamPage } from './stream/StreamPage';
import { StreamScriptTab } from './stream/StreamScriptTab';
import { StreamsPage } from './streams/StreamsPage';
import { TableDataTab } from './table/TableDataTab';
import { TableInfoTab } from './table/TableInfoTab';
import { TablePage } from './table/TablePage';
import { TableScriptTab } from './table/TableScriptTab';
import { TablesPage } from './tables/TablesPage';
import { SystemPage } from './template/SystemPage';



const routes = [
  {
    path: '',
    canActivate: [AuthGuard, InstanceExistsGuard],
    canActivateChild: [AuthGuard],
    component: SystemPage,
    children: [
      {
        path: 'dashboard',
        component: DashboardPage,
      },
      {
        path: 'clients',
        component: ClientsPage,
      },
      {
        path: 'links',
        component: LinksPage,
      },
      {
        path: 'processors',
        pathMatch: 'full',
        component: ProcessorsPage,
      },
      {
        path: 'processors/:name',
        component: ProcessorPage,
        children: [
          {
            path: '',
            redirectTo: 'tm',
          }, {
            path: 'tm',
            component: ProcessorTMTab,
          }, {
            path: 'tc',
            component: ProcessorTCTab,
          }
        ],
      },
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
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class SystemRoutingModule { }

export const routingComponents = [
  ClientsPage,
  DashboardPage,
  LinksPage,
  ProcessorsPage,
  ProcessorPage,
  ProcessorTCTab,
  ProcessorTMTab,
  ServicesPage,
  StreamsPage,
  StreamPage,
  StreamColumnsTab,
  StreamScriptTab,
  SystemPage,
  TablesPage,
  TablePage,
  TableInfoTab,
  TableDataTab,
  TableScriptTab,
];
