import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { DisplaysPage } from './displays/DisplaysPage';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MonitorPage } from './template/MonitorPage';
import { EventsPage } from './events/EventsPage';
import { MonitorToolbar } from './template/MonitorToolbar';

const routes = [
  {
    path: '',
    canActivate: [InstanceExistsGuard],
    component: MonitorPage,
    children: [
      {
        path: 'displays',
        component: DisplaysPage,
      },
      {
        path: 'events',
        component: EventsPage,
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
  DisplaysPage,
  EventsPage,
  MonitorPage,
  MonitorToolbar,
];
