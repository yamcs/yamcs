import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AttachContextGuard } from '../core/guards/AttachContextGuard';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { FailedTransfersTab } from './FailedTransfersTab';
import { FileTransferPage } from './FileTransferPage';
import { OngoingTransfersTab } from './OngoingTransfersTab';
import { SuccessfulTransfersTab } from './SuccessFulTransfersTab';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, AttachContextGuard],
    canActivateChild: [AuthGuard],
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
export class CfdpRoutingModule { }

export const routingComponents = [
  FailedTransfersTab,
  FileTransferPage,
  OngoingTransfersTab,
  SuccessfulTransfersTab,
];
