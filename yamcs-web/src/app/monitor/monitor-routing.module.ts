import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { DisplaysPageComponent } from './pages/displays.component';

const routes = [
  {
    path: '',
    component: DisplaysPageComponent,
    pathMatch: 'full',
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class MonitorRoutingModule { }

export const routingComponents = [
  DisplaysPageComponent,
];
