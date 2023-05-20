import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { FailedTransfersTab } from './FailedTransfersTab';
import { FileTransferPage } from './FileTransferPage';
import { OngoingTransfersTab } from './OngoingTransfersTab';
import { SuccessfulTransfersTab } from './SuccessFulTransfersTab';

const routes: Routes = [
  {
    path: '',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    component: InstancePage,
    children: [
      {
        path: '',
        component: FileTransferPage,
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'ongoing-transfers',
          }, {
            path: 'ongoing-transfers',
            component: OngoingTransfersTab,
          }, {
            path: 'failed-transfers',
            component: FailedTransfersTab,
          }, {
            path: 'successful-transfers',
            component: SuccessfulTransfersTab,
          }
        ]
      },
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class FileTransferRoutingModule { }

export const routingComponents = [
  FailedTransfersTab,
  FileTransferPage,
  OngoingTransfersTab,
  SuccessfulTransfersTab,
];
