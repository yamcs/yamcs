import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayControlCommandQueueGuard } from '../core/guards/MayControlCommandQueueGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { CommandHistoryPage } from './command-history/CommandHistoryPage';
import { CommandReportPage } from './command-sender/CommandReportPage';
import { ConfigureCommandPage } from './command-sender/ConfigureCommandPage';
import { SendCommandPage } from './command-sender/SendCommandPage';
import { QueuesPage } from './queues/QueuesPage';
import { RunStackPage } from './stacks/RunStackPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, InstanceExistsGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',
    component: InstancePage,
    children: [
      {
        path: 'send',
        pathMatch: 'full',
        component: SendCommandPage,
      },
      {
        path: 'send/:qualifiedName',
        component: ConfigureCommandPage,
      },
      {
        path: 'report/:commandId',
        component: CommandReportPage,
      },
      {
        path: 'history',
        component: CommandHistoryPage,
      },
      {
        path: 'queues',
        component: QueuesPage,
        canActivate: [MayControlCommandQueueGuard],
      },
      {
        path: 'stack',
        component: RunStackPage,
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class CommandingRoutingModule { }

export const routingComponents = [
  CommandHistoryPage,
  CommandReportPage,
  ConfigureCommandPage,
  QueuesPage,
  RunStackPage,
  SendCommandPage,
];
