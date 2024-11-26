import { Routes, UrlMatcher, UrlSegment } from '@angular/router';
import { ExtensionComponent } from '../appbase/extension/extension.component';
import { extensionMatcher } from '../appbase/extension/extension.matcher';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { RunScriptComponent } from './run-script/run-script.component';
import { StackFilePageDirtyGuard, stackFilePageDirtyGuardFn } from './run-stack/stack-file-dirty-guard/stack-file-dirty.guard';
import { StackFileLogComponent } from './run-stack/stack-file-log/stack-file-log.component';
import { StackFileSettingsComponent } from './run-stack/stack-file-settings/stack-file-settings.component';
import { StackFileComponent } from './run-stack/stack-file/stack-file.component';
import { StackFileService } from './run-stack/stack-file/StackFileService';
import { StackFolderComponent } from './run-stack/stack-folder/stack-folder.component';
import { StacksPageComponent } from './run-stack/stacks-page/stacks-page.component';

const objectNameMatcher: UrlMatcher = url => {
  let consumed = url;

  // Stop consuming at /-/
  // (handled by Angular again)
  const idx = url.findIndex(segment => segment.path === '-');
  if (idx !== -1) {
    consumed = url.slice(0, idx);
  }

  const objectName = consumed.map(segment => segment.path).join('/');
  return {
    consumed,
    posParams: {
      'objectName': new UrlSegment(objectName, {}),
    },
  };
};

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
    children: [{
      matcher: objectNameMatcher,
      providers: [
        StackFilePageDirtyGuard,
        StackFileService,
      ],
      canActivate: [StackFileService],
      canDeactivate: [stackFilePageDirtyGuardFn],
      children: [{
        path: '',
        pathMatch: 'full',
        component: StackFileComponent,
      }, {
        path: '-/log',
        component: StackFileLogComponent,
      }, {
        path: '-/settings',
        component: StackFileSettingsComponent,
      }]
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
