import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { TablesPageComponent } from './pages/tables.component';
import { TablePageComponent } from './pages/table.component';
import { StreamsPageComponent } from './pages/streams.component';
import { StreamPageComponent } from './pages/stream.component';

import { InstanceExistsGuard } from '../core/guards/instance-exists.guard';
import { SystemPageComponent } from './pages/system.component';
import { ServicesPageComponent } from './pages/services.component';
import { LinksPageComponent } from './pages/links.component';
import { ClientsPageComponent } from './pages/clients.component';
import { DashboardPageComponent } from './pages/dashboard.component';

const routes = [
  {
    path: '',
    canActivate: [InstanceExistsGuard],
    component: SystemPageComponent,
    children: [
      {
        path: 'dashboard',
        component: DashboardPageComponent,
      },
      {
        path: 'clients',
        component: ClientsPageComponent,
      },
      {
        path: 'links',
        component: LinksPageComponent,
      },
      {
        path: 'tables',
        component: TablesPageComponent,
        pathMatch: 'full',
      },
      {
        path: 'tables/:name',
        component: TablePageComponent,
      },
      {
        path: 'streams',
        component: StreamsPageComponent,
        pathMatch: 'full',
      },
      {
        path: 'streams/:name',
        component: StreamPageComponent,
      },
      {
        path: 'services',
        component: ServicesPageComponent,
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
  ClientsPageComponent,
  DashboardPageComponent,
  LinksPageComponent,
  ServicesPageComponent,
  StreamsPageComponent,
  StreamPageComponent,
  SystemPageComponent,
  TablesPageComponent,
  TablePageComponent,
];
