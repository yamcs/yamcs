import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayGetMissionDatabaseGuard } from '../core/guards/MayGetMissionDatabaseGuard';
import { AlgorithmPage } from './algorithms/AlgorithmPage';
import { AlgorithmsPage } from './algorithms/AlgorithmsPage';
import { CommandPage } from './commands/CommandPage';
import { CommandsPage } from './commands/CommandsPage';
import { ContainerPage } from './containers/ContainerPage';
import { ContainersPage } from './containers/ContainersPage';
import { ParameterChartTab } from './parameters/ParameterChartTab';
import { ParameterDataTab } from './parameters/ParameterDataTab';
import { ParameterPage } from './parameters/ParameterPage';
import { ParameterSummaryTab } from './parameters/ParameterSummaryTab';
import { ParametersPage } from './parameters/ParametersPage';
import { SpaceSystemAlgorithmsTab } from './space-system-detail/SpaceSystemAlgorithmsTab';
import { SpaceSystemChangelogTab } from './space-system-detail/SpaceSystemChangelogTab';
import { SpaceSystemCommandsTab } from './space-system-detail/SpaceSystemCommandsTab';
import { SpaceSystemContainersTab } from './space-system-detail/SpaceSystemContainersTab';
import { SpaceSystemPage } from './space-system-detail/SpaceSystemPage';
import { SpaceSystemParametersTab } from './space-system-detail/SpaceSystemParametersTab';
import { SpaceSystemsPage } from './space-systems/SpaceSystemsPage';
import { MdbPage } from './template/MdbPage';


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
