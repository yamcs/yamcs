import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { DisplaysPageComponent } from './pages/displays.component';
import { InstanceExistsGuard } from '../core/guards/instance-exists.guard';

const routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'todo',
  }, {
    path: ':instance',
    canActivate: [InstanceExistsGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
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
];
