import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { DisplaysPageComponent } from './pages/displays.component';
import { InstanceExistsGuard } from '../core/guards/instance-exists.guard';
import { MonitorPageComponent } from './pages/monitor.component';

const routes = [
  {
    path: '',
    canActivate: [InstanceExistsGuard],
    component: MonitorPageComponent,
    children: [
      {
        path: 'displays',
        component: DisplaysPageComponent,
      }
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class MonitorRoutingModule { }

export const routingComponents = [
  DisplaysPageComponent,
  MonitorPageComponent,
];
