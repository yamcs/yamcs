import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { CommandHistoryPage } from './command-history/CommandHistoryPage';
import { CommandQueuesPage } from './command-queues/CommandQueuesPage';

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
        redirectTo: 'command-history',
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
  CommandHistoryPage,
  CommandQueuesPage,
];
