import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { TablesPageComponent } from './pages/tables.component';
import { TablePageComponent } from './pages/table.component';
import { StreamsPageComponent } from './pages/streams.component';
import { StreamPageComponent } from './pages/stream.component';

const routes = [
  {
    path: 'tables',
    component: TablesPageComponent,
    pathMatch: 'full',
  }, {
    path: 'tables/:name',
    component: TablePageComponent,
  }, {
    path: 'streams',
    component: StreamsPageComponent,
    pathMatch: 'full',
  }, {
    path: 'streams/:name',
    component: StreamPageComponent,
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class SchemaRoutingModule { }

export const routingComponents = [
  TablesPageComponent,
  TablePageComponent,
  StreamsPageComponent,
  StreamPageComponent,
];
