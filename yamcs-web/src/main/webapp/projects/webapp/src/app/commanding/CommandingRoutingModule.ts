import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { mayControlCommandQueueGuardFn } from '../core/guards/MayControlCommandQueueGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { ActionLogTab as ClearanceActionLogTab } from './clearances/ActionLogTab';
import { clearancesEnabledGuardFn } from './clearances/ClearancesEnabledGuard';
import { ClearancesPage } from './clearances/ClearancesPage';
import { CommandHistoryPage } from './command-history/CommandHistoryPage';
import { CommandPage } from './command-history/CommandPage';
import { CommandReportPage } from './command-sender/CommandReportPage';
import { ConfigureCommandPage } from './command-sender/ConfigureCommandPage';
import { SendCommandPage } from './command-sender/SendCommandPage';
import { ActionLogTab as QueueActionLogTab } from './queues/ActionLogTab';
import { QueuedCommandsTab } from './queues/QueuedCommandsTab';
import { QueuesPage } from './queues/QueuesPage';
import { StackFilePage } from './stacks/StackFilePage';
import { StackFilePageDirtyGuard, stackFilePageDirtyGuardFn } from './stacks/StackFilePageDirtyGuard';
import { StackFolderPage } from './stacks/StackFolderPage';
import { StackPage } from './stacks/StackPage';
import { StacksPage } from './stacks/StacksPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    component: InstancePage,
    children: [
      {
        path: 'clearances',
        canActivate: [clearancesEnabledGuardFn],
        children: [
          {
            path: '',
            pathMatch: 'full',
            component: ClearancesPage,
          }, {
            path: 'log',
            component: ClearanceActionLogTab,
          }
        ]
      }, {
        path: 'send',
        pathMatch: 'full',
        component: SendCommandPage,
      }, {
        path: 'send/:qualifiedName',
        component: ConfigureCommandPage,
      }, {
        path: 'report/:commandId',
        component: CommandReportPage,
      }, {
        path: 'history',
        children: [{
          path: '',
          pathMatch: 'full',
          component: CommandHistoryPage,
        }, {
          path: ':commandId',
          component: CommandPage,
        }]
      }, {
        path: 'queues',
        component: QueuesPage,
        canActivate: [mayControlCommandQueueGuardFn],
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'pending',
          }, {
            path: 'pending',
            component: QueuedCommandsTab,
          }, {
            path: 'log',
            component: QueueActionLogTab,
          }
        ]
      }, {
        path: 'stacks',
        pathMatch: 'full',
        redirectTo: 'stacks/browse',
      }, {
        path: 'stacks/browse',
        component: StacksPage,
        children: [
          {
            path: '**',
            component: StackFolderPage,
          }
        ]
      }, {
        path: 'stacks/files',
        component: StackPage,
        children: [
          {
            path: '**',
            component: StackFilePage,
            canDeactivate: [stackFilePageDirtyGuardFn],
          }
        ]
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    StackFilePageDirtyGuard,
  ]
})
export class CommandingRoutingModule { }

export const routingComponents = [
  ClearancesPage,
  ClearanceActionLogTab,
  CommandHistoryPage,
  CommandPage,
  CommandReportPage,
  ConfigureCommandPage,
  QueuedCommandsTab,
  QueuesPage,
  SendCommandPage,
  StacksPage,
  StackFilePage,
  StackPage,
  StackFolderPage,
  QueueActionLogTab,
];
