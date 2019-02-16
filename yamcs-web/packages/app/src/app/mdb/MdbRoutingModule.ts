import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayGetMissionDatabaseGuard } from '../core/guards/MayGetMissionDatabaseGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { AlgorithmPage } from './algorithms/AlgorithmPage';
import { AlgorithmsPage } from './algorithms/AlgorithmsPage';
import { CommandPage } from './commands/CommandPage';
import { CommandsPage } from './commands/CommandsPage';
import { ContainerPage } from './containers/ContainerPage';
import { ContainersPage } from './containers/ContainersPage';
import { ParameterChartTab } from './parameters/ParameterChartTab';
import { ParameterDataTab } from './parameters/ParameterDataTab';
import { ParameterPage } from './parameters/ParameterPage';
import { ParametersPage } from './parameters/ParametersPage';
import { ParameterSummaryTab } from './parameters/ParameterSummaryTab';


const routes = [{
  path: '',
  canActivate: [AuthGuard, InstanceExistsGuard, MayGetMissionDatabaseGuard],
  canActivateChild: [AuthGuard, MayGetMissionDatabaseGuard],
  component: InstancePage,
  children: [{
    path: '',
    pathMatch: 'full',
    redirectTo: 'parameters',
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
  ParametersPage,
  ParameterPage,
  ParameterDataTab,
  ParameterChartTab,
  ParameterSummaryTab,
];
