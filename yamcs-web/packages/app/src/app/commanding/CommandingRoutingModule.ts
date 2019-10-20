import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayControlCommandQueueGuard } from '../core/guards/MayControlCommandQueueGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { CommandHistoryPage } from './command-history/CommandHistoryPage';
import { SendCommandPage as SendCommandPage } from './command-sender/SendCommandPage';
import { QueuesPage } from './queues/QueuesPage';

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
        path: 'history',
        component: CommandHistoryPage,
      },
      {
        path: 'queues',
        component: QueuesPage,
        canActivate: [MayControlCommandQueueGuard],
      },
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
  QueuesPage,
  SendCommandPage,
];
