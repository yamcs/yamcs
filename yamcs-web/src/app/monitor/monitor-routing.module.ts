import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { DisplaysPageComponent } from './pages/displays.component';
import { InstanceExistsGuard } from '../core/guards/instance-exists.guard';
import { MonitorPageComponent } from './pages/monitor.component';
import { EventsPageComponent } from './pages/events.component';
import { MonitorToolbarComponent } from './components/monitor-toolbar.component';

const routes = [
  {
    path: '',
    canActivate: [InstanceExistsGuard],
    component: MonitorPageComponent,
    children: [
      {
        path: 'displays',
        component: DisplaysPageComponent,
      },
      {
        path: 'events',
        component: EventsPageComponent,
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
  EventsPageComponent,
  MonitorPageComponent,
  MonitorToolbarComponent,
];
