import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild, input } from '@angular/core';
import { Activity, ActivityLog, ActivityLogSubscription, MessageService, Synchronizer, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { ActivityService } from '../shared/activity.service';

@Component({
  standalone: true,
  templateUrl: './activity-log-tab.component.html',
  styleUrl: './activity-log-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ActivityLogTabComponent implements OnDestroy {

  activityId = input.required<string>();
  activity$: Observable<Activity | null>;

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

  @ViewChild('logContainer')
  logContainer: ElementRef<HTMLDivElement>;

  @ViewChild('top')
  topAnchor: ElementRef<HTMLDivElement>;

  @ViewChild('bottom')
  bottomAnchor: ElementRef<HTMLDivElement>;

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private syncService: Synchronizer,
    activityService: ActivityService,
  ) {
    this.activity$ = activityService.activity$;
  }

  ngOnInit() {
    const { yamcs } = this;
    this.activityLogSubscription = yamcs.yamcsClient.createActivityLogSubscription({
      instance: yamcs.instance!,
      activity: this.activityId(),
    }, newLog => {
      // Discard logs we're already aware of from a REST response
      // (would be better to solve this with a seqnum)
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

    yamcs.yamcsClient.getActivityLog(yamcs.instance!, this.activityId()).then(logs => {
      this.archivedLogs = logs.filter(newLog => {
        // Discard logs we're already aware of from the WebSocket subscription
        // (would be better to solve this with a seqnum)
        for (const log of this.realtimeLogs) {
          if (newLog.time === log.time
            && newLog.level === log.level
            && newLog.message === log.message
            && newLog.source === log.source) {
            return false;
          }
        }
        return true;
      });
      this.emitLogs();
    }).catch(err => this.messageService.showError(err));

    this.syncSubscription = this.syncService.syncFast(() => {
      if (this.dirty$.value) {
        this.emitLogs();
        this.dirty$.next(false);
      }
    });
  }

  private emitLogs() {
    this.logs$.next([...this.archivedLogs, ...this.realtimeLogs]);

    if (this.logContainer) {
      const { nativeElement: el } = this.logContainer;
      const isAtBottom = Math.abs(el.scrollHeight - el.clientHeight - el.scrollTop) <= 1;
      if (isAtBottom) {
        setTimeout(() => { // T/O to allow for page update
          this.bottomAnchor.nativeElement.scrollIntoView();
        });
      }
    }
  }

  ngOnDestroy() {
    this.activityLogSubscription?.cancel();
    this.syncSubscription?.unsubscribe();
  }
}
