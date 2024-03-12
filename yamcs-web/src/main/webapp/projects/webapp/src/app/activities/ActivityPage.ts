import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { Activity, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../core/services/AuthService';
import { SetFailedDialog } from './SetFailedDialog';

@Component({
  templateUrl: './ActivityPage.html',
  styleUrls: ['./ActivityPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityPage {

  activity$ = new BehaviorSubject<Activity | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private authService: AuthService,
    private dialog: MatDialog,
  ) {
    const id = route.snapshot.paramMap.get('activityId')!;
    yamcs.yamcsClient.getActivity(yamcs.instance!, id).then(activity => {
      this.activity$.next(activity);
    }).catch(err => messageService.showError(err));
  }

  mayControlActivities() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlActivities');
  }

  setSuccessful(activity: Activity) {
    this.yamcs.yamcsClient.completeManualActivity(this.yamcs.instance!, activity.id)
      .then(activity => this.activity$.next(activity))
      .catch(err => this.messageService.showError(err));
  }

  setFailed(activity: Activity) {
    this.dialog.open(SetFailedDialog, {
      width: '400px',
      data: { activity },
    }).afterClosed().subscribe(result => {
      if (result) {
        this.yamcs.yamcsClient.completeManualActivity(this.yamcs.instance!, activity.id, {
          failureReason: result.failureReason,
        }).then(activity => this.activity$.next(activity))
          .catch(err => this.messageService.showError(err));
      }
    });
  }

  cancelActivity(activity: Activity) {
    this.yamcs.yamcsClient.cancelActivity(this.yamcs.instance!, activity.id)
      .catch(err => this.messageService.showError(err));
  }
}
