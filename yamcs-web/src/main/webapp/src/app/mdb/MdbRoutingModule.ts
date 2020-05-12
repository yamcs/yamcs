import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { MayGetMissionDatabaseGuard } from '../core/guards/MayGetMissionDatabaseGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { AlgorithmPage } from './algorithms/AlgorithmPage';
import { AlgorithmsPage } from './algorithms/AlgorithmsPage';
import { CommandPage } from './commands/CommandPage';
import { CommandsPage } from './commands/CommandsPage';
import { ContainerPage } from './containers/ContainerPage';
import { ContainersPage } from './containers/ContainersPage';
import { OverviewPage } from './overview/OverviewPage';
import { ParameterPage } from './parameters/ParameterPage';
import { ParametersPage } from './parameters/ParametersPage';


const routes = [{
  path: '',
  canActivate: [AuthGuard, AttachContextGuard, MayGetMissionDatabaseGuard],
  canActivateChild: [AuthGuard, MayGetMissionDatabaseGuard],
  component: InstancePage,
  children: [{
    path: '',
    pathMatch: 'full',
    component: OverviewPage,
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
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class MdbRoutingModule { }

export const routingComponents = [
  OverviewPage,
  AlgorithmsPage,
  AlgorithmPage,
  CommandsPage,
  CommandPage,
  ContainersPage,
  ContainerPage,
  ParametersPage,
  ParameterPage,
];
