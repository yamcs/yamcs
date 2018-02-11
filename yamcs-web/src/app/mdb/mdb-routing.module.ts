import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ParametersPageComponent } from './pages/parameters.component';
import { CommandsPageComponent } from './pages/commands.component';
import { InstanceExistsGuard } from '../core/guards/instance-exists.guard';
import { MdbPageComponent } from './pages/mdb.component';
import { SpaceSystemsPageComponent } from './pages/space-systems.component';
import { SpaceSystemPageComponent } from './pages/space-system.component';
import { SpaceSystemParametersTabComponent } from './pages/space-system-parameters.component';
import { SpaceSystemChangelogTabComponent } from './pages/space-system-changelog.component';

const routes = [
  {
    path: '',
    canActivate: [InstanceExistsGuard],
    component: MdbPageComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'space-systems'
      },
      {
        path: 'space-systems',
        children: [
          {
            path: '',
            pathMatch: 'full',
            component: SpaceSystemsPageComponent,
          }, {
            path: ':qualifiedName',
            component: SpaceSystemPageComponent,
            children: [
              {
                path: 'changelog',
                component: SpaceSystemChangelogTabComponent,
              }, {
                path: 'parameters',
                component: SpaceSystemParametersTabComponent,
              }
            ]
          }
        ]
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
  ParametersPageComponent,
  SpaceSystemsPageComponent,
  SpaceSystemPageComponent,
  SpaceSystemChangelogTabComponent,
  SpaceSystemParametersTabComponent,
];
