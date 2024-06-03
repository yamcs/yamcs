import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { Activity, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { Observable, tap } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { SetFailedDialogComponent } from '../set-failed-dialog/set-failed-dialog.component';
import { ActivityStatusComponent } from '../shared/activity-status.component';
import { ActivityService } from '../shared/activity.service';

@Component({
  standalone: true,
  templateUrl: './activity.component.html',
  styleUrl: './activity.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ActivityStatusComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ActivityComponent implements OnInit, OnDestroy {

  activityId = input.required<string>();
  activity$: Observable<Activity | null>;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private authService: AuthService,
    private dialog: MatDialog,
    private activityService: ActivityService,
  ) {
    this.activity$ = activityService.activity$.pipe(
      tap(activity => {
        if (activity) {
          title.setTitle(activity.detail);
        }
      })
    );
  }

  ngOnInit() {
    this.activityService.connect(this.activityId());
  }

  mayControlActivities() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlActivities');
  }

  setSuccessful(activity: Activity) {
    this.yamcs.yamcsClient.completeManualActivity(this.yamcs.instance!, activity.id)
      .catch(err => this.messageService.showError(err));
  }

  setFailed(activity: Activity) {
    this.dialog.open(SetFailedDialogComponent, {
      width: '400px',
      data: { activity },
    }).afterClosed().subscribe(result => {
      if (result) {
        this.yamcs.yamcsClient.completeManualActivity(this.yamcs.instance!, activity.id, {
          failureReason: result.failureReason,
        }).catch(err => this.messageService.showError(err));
      }
    });
  }

  cancelActivity(activity: Activity) {
    this.yamcs.yamcsClient.cancelActivity(this.yamcs.instance!, activity.id)
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.activityService.disconnect();
  }
}
