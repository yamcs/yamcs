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
import { AlgorithmPage } from './algorithms/AlgorithmPage';
import { SpaceSystemAlgorithmTab } from './space-system-detail/SpaceSystemAlgorithmTab';
import { SpaceSystemParameterTab } from './space-system-detail/SpaceSystemParameterTab';
import { ParameterPage } from './parameters/ParameterPage';
import { SpaceSystemContainerTab } from './space-system-detail/SpaceSystemContainerTab';
import { ContainerPage } from './containers/ContainerPage';
import { CommandPage } from './commands/CommandPage';
import { SpaceSystemCommandTab } from './space-system-detail/SpaceSystemCommandTab';

const routes = [{
  path: '',
  canActivate: [InstanceExistsGuard],
  component: MdbPage,
  children: [{
    path: '',
    pathMatch: 'full',
    redirectTo: 'space-systems',
  }, {
    path: 'space-systems',
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
          pathMatch: 'full',
          component: SpaceSystemAlgorithmsTab,
        }, {
          path: 'algorithms/:qualifiedName',
          component: SpaceSystemAlgorithmTab,
        }, {
          path: 'changelog',
          pathMatch: 'full',
          component: SpaceSystemChangelogTab,
        }, {
          path: 'commands',
          pathMatch: 'full',
          component: SpaceSystemCommandsTab,
        }, {
          path: 'commands/:qualifiedName',
          component: SpaceSystemCommandTab,
        }, {
          path: 'containers',
          pathMatch: 'full',
          component: SpaceSystemContainersTab,
        }, {
          path: 'containers/:qualifiedName',
          component: SpaceSystemContainerTab,
        }, {
          path: 'parameters',
          pathMatch: 'full',
          component: SpaceSystemParametersTab,
        }, {
          path: 'parameters/:qualifiedName',
          component: SpaceSystemParameterTab,
        }]
      }
    ]
  }, {
    path: 'algorithms',
    pathMatch: 'full',
    component: AlgorithmsPage,
  }, {
    path: 'algorithms/:qualifiedName',
    component: AlgorithmPage,
  }, {
    path: 'commands',
    pathMatch: 'full',
    component: CommandsPage,
  }, {
    path: 'commands/:qualifiedName',
    component: CommandPage,
  }, {
    path: 'containers',
    pathMatch: 'full',
    component: ContainersPage,
  }, {
    path: 'containers/:qualifiedName',
    component: ContainerPage,
  }, {
    path: 'parameters',
    pathMatch: 'full',
    component: ParametersPage,
  }, {
    path: 'parameters/:qualifiedName',
    component: ParameterPage,
  }]
}];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class MdbRoutingModule { }

export const routingComponents = [
  AlgorithmsPage,
  AlgorithmPage,
  CommandsPage,
  CommandPage,
  ContainersPage,
  ContainerPage,
  MdbPage,
  ParametersPage,
  ParameterPage,
  SpaceSystemsPage,
  SpaceSystemPage,
  SpaceSystemAlgorithmsTab,
  SpaceSystemAlgorithmTab,
  SpaceSystemChangelogTab,
  SpaceSystemCommandsTab,
  SpaceSystemCommandTab,
  SpaceSystemContainersTab,
  SpaceSystemContainerTab,
  SpaceSystemParametersTab,
  SpaceSystemParameterTab,
];
