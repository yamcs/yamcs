import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { FailedTransfersTabComponent } from './failed-transfers-tab/failed-transfers-tab.component';
import { FileTransferListComponent } from './file-transfer-list/file-transfer-list.component';
import { OngoingTransfersTabComponent } from './ongoing-transfers-tab/ongoing-transfers-tab.component';
import { SuccessfulTransfersTabComponent } from './successful-transfers-tab/successful-transfers-tab.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: '',
    component: FileTransferListComponent,
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: 'ongoing-transfers',
    }, {
      path: 'ongoing-transfers',
      component: OngoingTransfersTabComponent,
    }, {
      path: 'failed-transfers',
      component: FailedTransfersTabComponent,
    }, {
      path: 'successful-transfers',
      component: SuccessfulTransfersTabComponent,
    }]
  }]
}];
