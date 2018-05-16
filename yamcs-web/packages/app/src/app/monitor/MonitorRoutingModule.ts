import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayReadEventsGuard } from '../core/guards/MayReadEventsGuard';
import { AlarmsPage } from './alarms/AlarmsPage';
import { ArchivePage } from './archive/ArchivePage';
import { CommandsPage } from './commands/CommandsPage';
import { DisplaysPage } from './displays/DisplaysPage';
import { EventsPage } from './events/EventsPage';
import { ExtensionPage } from './ext/ExtensionPage';
import { LayoutPage } from './layouts/LayoutPage';
import { LayoutsPage } from './layouts/LayoutsPage';
import { MonitorPage } from './template/MonitorPage';
import { MonitorToolbar } from './template/MonitorToolbar';


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
        path: 'archive',
        component: ArchivePage,
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
  ArchivePage,
  CommandsPage,
  DisplaysPage,
  EventsPage,
  ExtensionPage,
  LayoutsPage,
  LayoutPage,
  MonitorPage,
  MonitorToolbar,
];
