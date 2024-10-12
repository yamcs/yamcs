import { Routes } from '@angular/router';
import { ExtensionComponent } from '../appbase/extension/extension.component';
import { extensionMatcher } from '../appbase/extension/extension.matcher';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { RunScriptComponent } from './run-script/run-script.component';
import { StackFilePageDirtyGuard, stackFilePageDirtyGuardFn } from './run-stack/stack-file-dirty-guard/stack-file-dirty.guard';
import { StackFileComponent } from './run-stack/stack-file/stack-file.component';
import { StackFolderComponent } from './run-stack/stack-folder/stack-folder.component';
import { StackPageComponent } from './run-stack/stack-page/stack-page.component';
import { StacksPageComponent } from './run-stack/stacks-page/stacks-page.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: 'stacks',
    pathMatch: 'full',
    redirectTo: 'stacks/browse',
  }, {
    path: 'stacks/browse',
    component: StacksPageComponent,
    children: [{
      path: '**',
      component: StackFolderComponent,
    }]
  }, {
    path: 'stacks/files',
    component: StackPageComponent,
    providers: [
      StackFilePageDirtyGuard,
    ],
    children: [{
      path: '**',
      component: StackFileComponent,
      canDeactivate: [stackFilePageDirtyGuardFn],
    }]
  }, {
    path: 'script',
    pathMatch: 'full',
    component: RunScriptComponent,
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
