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
import { ParameterPage } from './parameters/ParameterPage';
import { ContainerPage } from './containers/ContainerPage';
import { CommandPage } from './commands/CommandPage';
import { ParameterChartTab } from './parameters/ParameterChartTab';
import { ParameterDataTab } from './parameters/ParameterDataTab';
import { ParameterSummaryTab } from './parameters/ParameterSummaryTab';
import { AuthGuard } from '../core/guards/AuthGuard';
import { MayGetMissionDatabaseGuard } from '../core/guards/MayGetMissionDatabaseGuard';

const routes = [{
  path: '',
  canActivate: [AuthGuard, InstanceExistsGuard, MayGetMissionDatabaseGuard],
  canActivateChild: [AuthGuard, MayGetMissionDatabaseGuard],
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
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'summary'
      },
      {
        path: 'summary',
        component: ParameterSummaryTab,
      },
      {
        path: 'chart',
        component: ParameterChartTab,
      },
      {
        path: 'data',
        component: ParameterDataTab,
      }
    ]
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
  ParameterDataTab,
  ParameterChartTab,
  ParameterSummaryTab,
  SpaceSystemsPage,
  SpaceSystemPage,
  SpaceSystemAlgorithmsTab,
  SpaceSystemChangelogTab,
  SpaceSystemCommandsTab,
  SpaceSystemContainersTab,
  SpaceSystemParametersTab,
];
