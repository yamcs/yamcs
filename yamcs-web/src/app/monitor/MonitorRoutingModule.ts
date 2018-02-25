import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { DisplaysPage } from './displays/DisplaysPage';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MonitorPage } from './template/MonitorPage';
import { EventsPage } from './events/EventsPage';
import { MonitorToolbar } from './template/MonitorToolbar';
import { LayoutsPage } from './layouts/LayoutsPage';
import { LayoutPage } from './layouts/LayoutPage';
import { AlarmsPage } from './alarms/AlarmsPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [InstanceExistsGuard],
    runGuardsAndResolvers: 'always',  // See DisplaysPage.ts for documentation
    component: MonitorPage,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'displays',
      },
      {
        path: 'alarms',
        component: AlarmsPage,
      },
      {
        path: 'displays',
        component: DisplaysPage,
      },
      {
        path: 'events',
        component: EventsPage,
      },
      {
        path: 'layouts',
        pathMatch: 'full',
        component: LayoutsPage,
      },
      {
        path: 'layouts/:name',
        component: LayoutPage,
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
  AlarmsPage,
  DisplaysPage,
  EventsPage,
  LayoutsPage,
  LayoutPage,
  MonitorPage,
  MonitorToolbar,
];
