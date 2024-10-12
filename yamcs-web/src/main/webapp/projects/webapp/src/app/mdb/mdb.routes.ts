import { Routes } from '@angular/router';
import { ExtensionComponent } from '../appbase/extension/extension.component';
import { extensionMatcher } from '../appbase/extension/extension.matcher';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { mayGetMissionDatabaseGuardFn } from '../core/guards/MayGetMissionDatabaseGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { AlgorithmListComponent } from './algorithms/algorithm-list/algorithm-list.component';
import { AlgorithmComponent } from './algorithms/algorithm/algorithm.component';
import { CommandListComponent } from './commands/command-list/command-list.component';
import { CommandComponent } from './commands/command/command.component';
import { ContainerListComponent } from './containers/container-list/container-list.component';
import { ContainerComponent } from './containers/container/container.component';
import { OverviewComponent } from './overview/overview-component';
import { ParameterTypesComponent } from './parameter-types/parameter-type-list/parameter-type-list.component';
import { ParameterTypeComponent } from './parameter-types/parameter-type/parameter-type.component';
import { ParameterListComponent } from './parameters/parameter-list/parameter-list.component';
import { ParameterComponent } from './parameters/parameter/parameter.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn, mayGetMissionDatabaseGuardFn],
  canActivateChild: [authGuardChildFn, mayGetMissionDatabaseGuardFn],
  component: InstancePageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    component: OverviewComponent,
  }, {
    path: 'algorithms',
    pathMatch: 'full',
    component: AlgorithmListComponent,
  }, {
    path: 'algorithms/:qualifiedName',
    component: AlgorithmComponent,
  }, {
    path: 'commands',
    pathMatch: 'full',
    component: CommandListComponent,
  }, {
    path: 'commands/:qualifiedName',
    component: CommandComponent,
  }, {
    path: 'containers',
    pathMatch: 'full',
    component: ContainerListComponent,
  }, {
    path: 'containers/:qualifiedName',
    component: ContainerComponent,
  }, {
    path: 'parameters',
    pathMatch: 'full',
    component: ParameterListComponent,
  }, {
    path: 'parameters/:qualifiedName',
    component: ParameterComponent,
  }, {
    path: 'parameter-types',
    pathMatch: 'full',
    component: ParameterTypesComponent,
  }, {
    path: 'parameter-types/:qualifiedName',
    component: ParameterTypeComponent,
  }, {
    path: 'ext',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    children: [{
      matcher: extensionMatcher,
      component: ExtensionComponent,
    }]
  }]
}];
