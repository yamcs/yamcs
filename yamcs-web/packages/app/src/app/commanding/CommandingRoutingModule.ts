import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { CommandHistoryPage } from './command-history/CommandHistoryPage';
import { CommandQueuesPage } from './command-queues/CommandQueuesPage';
import { CommandStackPage } from './command-stack/CommandStackPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, InstanceExistsGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',
    component: InstancePage,
    children: [
      {
        path: 'command-stack',
        component: CommandStackPage,
      },
      {
        path: 'command-history',
        component: CommandHistoryPage,
      },
      {
        path: 'command-queues',
        component: CommandQueuesPage,
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
  CommandStackPage,
  CommandHistoryPage,
  CommandQueuesPage,
];
