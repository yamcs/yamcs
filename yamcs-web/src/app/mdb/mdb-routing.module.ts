import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { OverviewPageComponent } from './pages/overview.component';
import { ParametersPageComponent } from './pages/parameters.component';
import { CommandsPageComponent } from './pages/commands.component';
import { InstanceExistsGuard } from '../core/guards/instance-exists.guard';
import { MdbPageComponent } from './pages/mdb.component';

const routes = [
  {
    path: '',
    canActivate: [InstanceExistsGuard],
    component: MdbPageComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: OverviewPageComponent,
      },
      {
        path: 'parameters',
        component: ParametersPageComponent,
      },
      {
        path: 'commands',
        component: CommandsPageComponent,
      }
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class MdbRoutingModule { }

export const routingComponents = [
  CommandsPageComponent,
  MdbPageComponent,
  OverviewPageComponent,
  ParametersPageComponent,
];
