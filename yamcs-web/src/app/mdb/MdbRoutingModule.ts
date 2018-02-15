import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ParametersPage } from './parameters/ParametersPage';
import { CommandsPage } from './commands/CommandsPage';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MdbPage } from './template/MdbPage';
import { SpaceSystemsPage } from './space-systems/SpaceSystemsPage';
import { SpaceSystemPage } from './space-system-detail/SpaceSystemPage';
import { SpaceSystemParametersTab } from './space-system-detail/SpaceSystemParametersTab';
import { SpaceSystemChangelogTab } from './space-system-detail/SpaceSystemChangelogTab';
import { AlgorithmsPage } from './algorithms/AlgorithmsPage';
import { SpaceSystemAlgorithmsTab } from './space-system-detail/SpaceSystemAlgorithmsTab';
import { ContainersPage } from './containers/ContainersPage';
import { SpaceSystemContainersTab } from './space-system-detail/SpaceSystemContainersTab';
import { SpaceSystemCommandsTab } from './space-system-detail/SpaceSystemCommandsTab';

const routes = [{
  path: '',
  canActivate: [InstanceExistsGuard],
  component: MdbPage,
  children: [{
    path: '',
    pathMatch: 'full',
    redirectTo: 'SpaceSystems'
  }, {
    path: 'SpaceSystems',
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: SpaceSystemsPage,
      }, {
        path: ':qualifiedName',
        component: SpaceSystemPage,
        children: [{
          path: '',
          pathMatch: 'full',
          redirectTo: 'parameters'
        }, {
          path: 'algorithms',
          component: SpaceSystemAlgorithmsTab,
        }, {
          path: 'changelog',
          component: SpaceSystemChangelogTab,
        }, {
          path: 'commands',
          component: SpaceSystemCommandsTab,
        }, {
          path: 'containers',
          component: SpaceSystemContainersTab,
        }, {
          path: 'parameters',
          component: SpaceSystemParametersTab,
        }]
      }
    ]
  }, {
    path: 'algorithms',
    component: AlgorithmsPage,
  }, {
    path: 'commands',
    component: CommandsPage,
  }, {
    path: 'containers',
    component: ContainersPage,
  }, {
    path: 'parameters',
    component: ParametersPage,
  }]
}];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class MdbRoutingModule { }

export const routingComponents = [
  AlgorithmsPage,
  CommandsPage,
  ContainersPage,
  MdbPage,
  ParametersPage,
  SpaceSystemsPage,
  SpaceSystemPage,
  SpaceSystemAlgorithmsTab,
  SpaceSystemChangelogTab,
  SpaceSystemCommandsTab,
  SpaceSystemContainersTab,
  SpaceSystemParametersTab,
];
