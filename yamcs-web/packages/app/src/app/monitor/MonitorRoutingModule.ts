import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayReadEventsGuard } from '../core/guards/MayReadEventsGuard';
import { AlarmsPage } from './alarms/AlarmsPage';
import { ArchivePage } from './archive/ArchivePage';
import { CommandingPage } from './commands/CommandingPage';
import { DisplayFilePage } from './displays/DisplayFilePage';
import { DisplayFilePageDirtyGuard } from './displays/DisplayFilePageDirtyGuard';
import { DisplayFolderPage } from './displays/DisplayFolderPage';
import { DisplayPage } from './displays/DisplayPage';
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
        redirectTo: 'displays/browse',
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
        path: 'commanding',
        component: CommandingPage,
      },
      {
        path: 'displays',
        pathMatch: 'full',
        redirectTo: 'displays/browse'
      },
      {
        path: 'displays/browse',
        component: DisplaysPage,
        children: [
          {
            path: '**',
            component: DisplayFolderPage,
          }
        ]
      },
      {
        path: 'displays/files',
        component: DisplayPage,
        children: [
          {
            path: '**',
            component: DisplayFilePage,
            canDeactivate: [DisplayFilePageDirtyGuard],
          }
        ]
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
  providers: [
    DisplayFilePageDirtyGuard,
  ]
})
export class MonitorRoutingModule { }

export const routingComponents = [
  AlarmsPage,
  ArchivePage,
  CommandingPage,
  DisplaysPage,
  DisplayFilePage,
  DisplayFolderPage,
  DisplayPage,
  EventsPage,
  ExtensionPage,
  LayoutsPage,
  LayoutPage,
  MonitorPage,
  MonitorToolbar,
];
