import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ActivityLog, ActivityLogSubscription, MessageService, Synchronizer, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  templateUrl: './ActivityLogTab.html',
  styleUrls: ['./ActivityLogTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityLogTab implements OnDestroy {

  // Separate archived and realtime logs, so that we can sort them correctly.
  //
  // - Archived logs arrive in correct order (based on persistence key).
  // - Realtime logs arrive in correct order, but are removed if they are identical to
  // an archived log.
  private archivedLogs: ActivityLog[] = [];
  private realtimeLogs: ActivityLog[] = [];

  logs$ = new BehaviorSubject<ActivityLog[]>([]);
  private dirty$ = new BehaviorSubject<boolean>(false);

  private syncSubscription: Subscription;
  private activityLogSubscription: ActivityLogSubscription;

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    messageService: MessageService,
    syncService: Synchronizer,
  ) {
    const activityId = route.parent!.snapshot.paramMap.get('activityId')!;
    this.activityLogSubscription = yamcs.yamcsClient.createActivityLogSubscription({
      instance: yamcs.instance!,
      activity: activityId,
    }, newLog => {
      // Discard logs we're already aware of from a REST response
      for (const log of this.archivedLogs) {
        if (newLog.time === log.time
          && newLog.level === log.level
          && newLog.message === log.message
          && newLog.source === log.source) {
          return;
        }
      }
      this.realtimeLogs.push(newLog);
      this.dirty$.next(true);
    });
    yamcs.yamcsClient.getActivityLog(yamcs.instance!, activityId).then(logs => {
      this.archivedLogs = logs;
      this.emitLogs();
    }).catch(err => messageService.showError(err));

    this.syncSubscription = syncService.syncFast(() => {
      if (this.dirty$.value) {
        this.emitLogs();
        this.dirty$.next(false);
      }
    });
  }

  private emitLogs() {
    this.logs$.next([...this.archivedLogs, ...this.realtimeLogs]);
  }

  ngOnDestroy() {
    this.activityLogSubscription?.cancel();
    this.syncSubscription?.unsubscribe();
  }
}
