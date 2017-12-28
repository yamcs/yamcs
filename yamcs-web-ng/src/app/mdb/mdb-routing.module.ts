import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { MdbComponent } from './mdb.component';
import { OverviewPageComponent } from './pages/overview.component';

const routes = [
  {
    path: '', component: OverviewPageComponent
  },
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class MdbRoutingModule { }

export const routingComponents = [
  MdbComponent,
  OverviewPageComponent,
];
