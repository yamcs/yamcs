import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { ActionLogTabComponent } from './action-log-tab/action-log-tab.component';
import { FileTransferListComponent } from './file-transfer-list/file-transfer-list.component';
import { resolveServices } from './file-transfer.resolvers';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    component: FileTransferListComponent,
    resolve: {
      services: resolveServices,
    },
  }, {
    path: 'log',
    component: ActionLogTabComponent,
  }]
}];
