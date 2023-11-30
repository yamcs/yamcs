import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { mayGetMissionDatabaseGuardFn } from '../core/guards/MayGetMissionDatabaseGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { AlgorithmPage } from './algorithms/AlgorithmPage';
import { AlgorithmsPage } from './algorithms/AlgorithmsPage';
import { CommandPage } from './commands/CommandPage';
import { CommandsPage } from './commands/CommandsPage';
import { ContainerPage } from './containers/ContainerPage';
import { ContainersPage } from './containers/ContainersPage';
import { OverviewPage } from './overview/OverviewPage';
import { ParameterTypePage } from './parameterTypes/ParameterTypePage';
import { ParameterTypesPage } from './parameterTypes/ParameterTypesPage';
import { ParameterPage } from './parameters/ParameterPage';
import { ParametersPage } from './parameters/ParametersPage';


const routes: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn, mayGetMissionDatabaseGuardFn],
  canActivateChild: [authGuardChildFn, mayGetMissionDatabaseGuardFn],
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
  }, {
    path: 'parameter-types',
    pathMatch: 'full',
    component: ParameterTypesPage,
  }, {
    path: 'parameter-types/:qualifiedName',
    component: ParameterTypePage,
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
  ParameterTypesPage,
  ParameterTypePage,
];
