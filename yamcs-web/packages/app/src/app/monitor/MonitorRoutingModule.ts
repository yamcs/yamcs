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
import { CommandsPage } from './commands/CommandsPage';
import { ExtensionPage } from './ext/ExtensionPage';
import { AuthGuard } from '../core/guards/AuthGuard';
import { MayReadEventsGuard } from '../core/guards/MayReadEventsGuard';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, InstanceExistsGuard],
    canActivateChild: [AuthGuard],
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
        path: 'commands',
        component: CommandsPage,
      },
      {
        path: 'displays',
        component: DisplaysPage,
      },
      {
        path: 'events',
        component: EventsPage,
        canActivate: [MayReadEventsGuard],
      },
      {
        path: 'layouts',
        pathMatch: 'full',
        component: LayoutsPage,
      },
      {
        path: 'layouts/:name',
        component: LayoutPage,
      },
      {
        path: 'ext/:name',
        component: ExtensionPage,
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
  CommandsPage,
  DisplaysPage,
  EventsPage,
  ExtensionPage,
  LayoutsPage,
  LayoutPage,
  MonitorPage,
  MonitorToolbar,
];
