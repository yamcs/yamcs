import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayControlCommandQueueGuard } from '../core/guards/MayControlCommandQueueGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { CommandHistoryPage } from './command-history/CommandHistoryPage';
import { CommandQueuesPage } from './command-queues/CommandQueuesPage';
import { SendCommandPage as SendCommandPage } from './command-sender/SendCommandPage';

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
        path: 'command-history',
        component: CommandHistoryPage,
      },
      {
        path: 'command-queues',
        component: CommandQueuesPage,
        canActivate: [MayControlCommandQueueGuard],
      },
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class CommandingRoutingModule { }

export const routingComponents = [
  CommandHistoryPage,
  CommandQueuesPage,
  SendCommandPage,
];
