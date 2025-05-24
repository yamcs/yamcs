import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  OnChanges,
  OnDestroy,
  viewChild,
} from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import {
  Activity,
  ActivityLog,
  ActivityLogSubscription,
  AuthService,
  MessageService,
  Synchronizer,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AddMessageDialogComponent } from '../../activities/activity-log-tab/add-message-dialog.component';

@Component({
  selector: 'app-item-detail-tab-logs',
  templateUrl: './item-detail-tab-logs.component.html',
  styleUrl: './item-detail-tab-logs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ItemDetailTabLogsComponent implements OnChanges, OnDestroy {
  private authService = inject(AuthService);
  private dialog = inject(MatDialog);
  private messageService = inject(MessageService);
  private synchronizer = inject(Synchronizer);
  private yamcs = inject(YamcsService);

  activity = input.required<Activity>();

  logContainer = viewChild.required<ElementRef<HTMLDivElement>>('logContainer');
  topAnchor = viewChild.required<ElementRef<HTMLDivElement>>('top');
  bottomAnchor = viewChild.required<ElementRef<HTMLDivElement>>('bottom');

  // Separate archived and realtime logs, so that we can sort them correctly.
  //
  // - Archived logs arrive in correct order (based on persistence key).
  // - Realtime logs arrive in correct order, but are removed if they are identical to
  // an archived log.
  private archivedLogs: ActivityLog[] = [];
  private realtimeLogs: ActivityLog[] = [];

  logs$ = new BehaviorSubject<ActivityLog[]>([]);
  private dirty$ = new BehaviorSubject<boolean>(false);

  private syncSubscription?: Subscription;
  private activityLogSubscription?: ActivityLogSubscription;

  // While the tab is open, follow the selection in the items list
  ngOnChanges(): void {
    if (!this.mayReadActivities()) {
      return;
    }

    // Clear state
    this.syncSubscription?.unsubscribe();
    this.activityLogSubscription?.cancel();
    this.archivedLogs.length = 0;
    this.realtimeLogs.length = 0;
    this.logs$.next([]);
    this.dirty$.next(false);

    // Setup for current activity
    this.setupRealtimeLogs();
    this.setupArchiveLogs();

    this.syncSubscription = this.synchronizer.syncFast(() => {
      if (this.dirty$.value) {
        this.emitLogs();
        this.dirty$.next(false);
      }
    });
  }

  private setupRealtimeLogs() {
    this.activityLogSubscription =
      this.yamcs.yamcsClient.createActivityLogSubscription(
        {
          instance: this.yamcs.instance!,
          activity: this.activity().id,
        },
        (newLog) => {
          // Discard logs we're already aware of from a REST response
          // (would be better to solve this with a seqnum)
          for (const log of this.archivedLogs) {
            if (
              newLog.time === log.time &&
              newLog.level === log.level &&
              newLog.message === log.message &&
              newLog.source === log.source
            ) {
              return;
            }
          }
          this.realtimeLogs.push(newLog);
          this.dirty$.next(true);
        },
      );
  }

  private setupArchiveLogs() {
    this.yamcs.yamcsClient
      .getActivityLog(this.yamcs.instance!, this.activity().id)
      .then((logs) => {
        this.archivedLogs = logs.filter((newLog) => {
          // Discard logs we're already aware of from the WebSocket subscription
          // (would be better to solve this with a seqnum)
          for (const log of this.realtimeLogs) {
            if (
              newLog.time === log.time &&
              newLog.level === log.level &&
              newLog.message === log.message &&
              newLog.source === log.source
            ) {
              return false;
            }
          }
          return true;
        });
        this.emitLogs();
      })
      .catch((err) => this.messageService.showError(err));
  }

  private emitLogs() {
    this.logs$.next([...this.archivedLogs, ...this.realtimeLogs]);

    if (this.logContainer) {
      const { nativeElement: el } = this.logContainer();
      const isAtBottom =
        Math.abs(el.scrollHeight - el.clientHeight - el.scrollTop) <= 1;
      if (isAtBottom) {
        setTimeout(() => {
          // T/O to allow for page update
          this.bottomAnchor().nativeElement.scrollIntoView();
        });
      }
    }
  }

  openAddMessageDialog() {
    this.dialog
      .open(AddMessageDialogComponent, {
        width: '500px',
      })
      .afterClosed()
      .subscribe((message) => {
        if (message) {
          this.yamcs.yamcsClient
            .addActivityLogMessage(
              this.yamcs.instance!,
              this.activity().id,
              message,
            )
            .catch((err) => this.messageService.showError(err));
        }
      });
  }

  mayReadActivities() {
    return this.authService.getUser()!.hasSystemPrivilege('ReadActivities');
  }

  mayControlActivities() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlActivities');
  }

  ngOnDestroy(): void {
    this.syncSubscription?.unsubscribe();
    this.activityLogSubscription?.cancel();
  }
}
