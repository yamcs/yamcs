import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { TablesPage } from './pages/TablesPage';
import { TablePage } from './pages/TablePage';
import { StreamsPage } from './pages/StreamsPage';
import { StreamPage } from './pages/StreamPage';

import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { SystemPage } from './pages/SystemPage';
import { ServicesPage } from './pages/ServicesPage';
import { LinksPage } from './pages/LinksPage';
import { ClientsPage } from './pages/ClientsPage';
import { DashboardPage } from './pages/DashboardPage';

const routes = [
  {
    path: '',
    canActivate: [InstanceExistsGuard],
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
        path: 'tables',
        component: TablesPage,
        pathMatch: 'full',
      },
      {
        path: 'tables/:name',
        component: TablePage,
      },
      {
        path: 'streams',
        component: StreamsPage,
        pathMatch: 'full',
      },
      {
        path: 'streams/:name',
        component: StreamPage,
      },
      {
        path: 'services',
        component: ServicesPage,
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
  ServicesPage,
  StreamsPage,
  StreamPage,
  SystemPage,
  TablesPage,
  TablePage,
];
