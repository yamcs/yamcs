import { Routes } from '@angular/router';
import { ExtensionComponent } from '../appbase/extension/extension.component';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { RunScriptComponent } from './run-script/run-script.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: 'script',
    pathMatch: 'full',
    component: RunScriptComponent,
  }, {
    path: 'ext',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    children: [{
      path: ':extension',
      component: ExtensionComponent,
    }]
  }]
}];
