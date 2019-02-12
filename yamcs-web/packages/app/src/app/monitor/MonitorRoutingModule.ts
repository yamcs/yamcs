import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayControlServicesGuard } from '../core/guards/MayControlServicesGuard';
import { MayReadEventsGuard } from '../core/guards/MayReadEventsGuard';
import { MayReadTablesGuard } from '../core/guards/MayReadTablesGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { AlarmsPage } from './alarms/AlarmsPage';
import { ArchivePage } from './archive/ArchivePage';
import { CommandHistoryPage } from './commands/CommandHistoryPage';
import { DisplayFilePage } from './displays/DisplayFilePage';
import { DisplayFilePageDirtyGuard } from './displays/DisplayFilePageDirtyGuard';
import { DisplayFolderPage } from './displays/DisplayFolderPage';
import { DisplayPage } from './displays/DisplayPage';
import { DisplaysPage } from './displays/DisplaysPage';
import { EventsPage } from './events/EventsPage';
import { ExtensionPage } from './ext/ExtensionPage';
import { JvmPage } from './jvm/JvmPage';
import { LayoutPage } from './layouts/LayoutPage';
import { LayoutsPage } from './layouts/LayoutsPage';
import { LinksPage } from './links/LinksPage';
import { ProcessorPage } from './processors/ProcessorPage';
import { ProcessorServicesTab } from './processors/ProcessorServicesTab';
import { ProcessorsPage } from './processors/ProcessorsPage';
import { ProcessorTCTab } from './processors/ProcessorTCTab';
import { ProcessorTMTab } from './processors/ProcessorTMTab';
import { ServicesPage } from './services/ServicesPage';
import { StreamColumnsTab } from './stream/StreamColumnsTab';
import { StreamPage } from './stream/StreamPage';
import { StreamScriptTab } from './stream/StreamScriptTab';
import { StreamsPage } from './streams/StreamsPage';
import { TableDataTab } from './table/TableDataTab';
import { TableInfoTab } from './table/TableInfoTab';
import { TablePage } from './table/TablePage';
import { TableScriptTab } from './table/TableScriptTab';
import { TablesPage } from './tables/TablesPage';


const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, InstanceExistsGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',  // See DisplaysPage.ts for documentation
    component: InstancePage,
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
        path: 'command-history',
        component: CommandHistoryPage,
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
        path: 'links',
        component: LinksPage,
      },
      {
        path: 'processors',
        pathMatch: 'full',
        component: ProcessorsPage,
      },
      {
        path: 'processors/:name',
        component: ProcessorPage,
        children: [
          {
            path: '',
            redirectTo: 'services',
          }, {
            path: 'services',
            component: ProcessorServicesTab,
          }, {
            path: 'tm',
            component: ProcessorTMTab,
          }, {
            path: 'tc',
            component: ProcessorTCTab,
          }
        ],
      },
      {
        path: 'tables',
        pathMatch: 'full',
        component: TablesPage,
        canActivate: [MayReadTablesGuard],
      },
      {
        path: 'tables/:name',
        component: TablePage,
        canActivate: [MayReadTablesGuard],
        canActivateChild: [MayReadTablesGuard],
        children: [
          {
            path: '',
            redirectTo: 'info',
          }, {
            path: 'info',
            component: TableInfoTab,
          }, {
            path: 'data',
            component: TableDataTab,
          }, {
            path: 'script',
            component: TableScriptTab,
          }
        ],
      },
      {
        path: 'streams',
        component: StreamsPage,
        pathMatch: 'full',
      },
      {
        path: 'streams/:name',
        component: StreamPage,
        children: [
          {
            path: '',
            redirectTo: 'columns',
          }, {
            path: 'columns',
            component: StreamColumnsTab,
          }, {
            path: 'script',
            component: StreamScriptTab,
          }
        ],
      },
      {
        path: 'services',
        component: ServicesPage,
        canActivate: [MayControlServicesGuard],
      },
      {
        path: 'jvm',
        component: JvmPage,
        canActivate: [AuthGuard],
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
  CommandHistoryPage,
  DisplaysPage,
  DisplayFilePage,
  DisplayFolderPage,
  DisplayPage,
  EventsPage,
  ExtensionPage,
  JvmPage,
  LayoutsPage,
  LayoutPage,
  LinksPage,
  ProcessorsPage,
  ProcessorPage,
  ProcessorServicesTab,
  ProcessorTCTab,
  ProcessorTMTab,
  ServicesPage,
  StreamsPage,
  StreamPage,
  StreamColumnsTab,
  StreamScriptTab,
  TablesPage,
  TablePage,
  TableInfoTab,
  TableDataTab,
  TableScriptTab,
];
