import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ParametersPageComponent } from './parameters/parameters.page';
import { CommandsPageComponent } from './commands/commands.page';
import { InstanceExistsGuard } from '../core/guards/instance-exists.guard';
import { MdbPageComponent } from './template/mdb.page';
import { SpaceSystemsPageComponent } from './space-systems/space-systems.page';
import { SpaceSystemPageComponent } from './space-system-detail/space-system.page';
import { SpaceSystemParametersTabComponent } from './space-system-detail/space-system-parameters.tab';
import { SpaceSystemChangelogTabComponent } from './space-system-detail/space-system-changelog.tab';
import { AlgorithmsPageComponent } from './algorithms/algorithms.page';
import { SpaceSystemAlgorithmsTabComponent } from './space-system-detail/space-system-algorithms.tab';
import { ContainersPageComponent } from './containers/containers.page';
import { SpaceSystemContainersTabComponent } from './space-system-detail/space-system-containers.tab';
import { SpaceSystemCommandsTabComponent } from './space-system-detail/space-system-commands.tab';

const routes = [{
  path: '',
  canActivate: [InstanceExistsGuard],
  component: MdbPageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    redirectTo: 'space-systems'
  }, {
    path: 'space-systems',
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: SpaceSystemsPageComponent,
      }, {
        path: ':qualifiedName',
        component: SpaceSystemPageComponent,
        children: [{
          path: '',
          pathMatch: 'full',
          redirectTo: 'parameters'
        }, {
          path: 'algorithms',
          component: SpaceSystemAlgorithmsTabComponent,
        }, {
          path: 'changelog',
          component: SpaceSystemChangelogTabComponent,
        }, {
          path: 'commands',
          component: SpaceSystemCommandsTabComponent,
        }, {
          path: 'containers',
          component: SpaceSystemContainersTabComponent,
        }, {
          path: 'parameters',
          component: SpaceSystemParametersTabComponent,
        }]
      }
    ]
  }, {
    path: 'algorithms',
    component: AlgorithmsPageComponent,
  }, {
    path: 'commands',
    component: CommandsPageComponent,
  }, {
    path: 'containers',
    component: ContainersPageComponent,
  }, {
    path: 'parameters',
    component: ParametersPageComponent,
  }]
}];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class MdbRoutingModule { }

export const routingComponents = [
  AlgorithmsPageComponent,
  CommandsPageComponent,
  ContainersPageComponent,
  MdbPageComponent,
  ParametersPageComponent,
  SpaceSystemsPageComponent,
  SpaceSystemPageComponent,
  SpaceSystemAlgorithmsTabComponent,
  SpaceSystemChangelogTabComponent,
  SpaceSystemCommandsTabComponent,
  SpaceSystemContainersTabComponent,
  SpaceSystemParametersTabComponent,
];
