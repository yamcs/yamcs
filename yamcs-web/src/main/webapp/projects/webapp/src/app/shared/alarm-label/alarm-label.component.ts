import { LowerCasePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnDestroy,
} from '@angular/core';
import {
  AuthService,
  FaviconService,
  GlobalAlarmStatus,
  GlobalAlarmStatusSubscription,
  WebappSdkModule,
  YamcsService,
  YaSidenav,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, map, Subscription } from 'rxjs';

@Component({
  selector: 'app-alarm-label',
  templateUrl: './alarm-label.component.html',
  styleUrl: './alarm-label.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LowerCasePipe, WebappSdkModule],
})
export class AlarmLabelComponent implements OnDestroy {
  private connectionInfoSubscription: Subscription;

  context$ = new BehaviorSubject<string | null>(null);
  status$ = new BehaviorSubject<GlobalAlarmStatus | null>(null);
  miniBackgroundColor$ = this.status$.pipe(
    map((status) => {
      if (status?.unacknowledgedCount) {
        switch (status.unacknowledgedSeverity) {
          case 'WATCH':
          case 'WARNING':
            return '#ff983059';
          case 'DISTRESS':
          case 'CRITICAL':
          case 'SEVERE':
            return '#f2495c59';
        }
      }
    }),
  );

  private sidenav = inject(YaSidenav);
  mini = this.sidenav.collapseItem;

  private statusSubscription: GlobalAlarmStatusSubscription;

  constructor(
    readonly yamcs: YamcsService,
    readonly faviconService: FaviconService,
    authService: AuthService,
  ) {
    this.connectionInfoSubscription = yamcs.connectionInfo$.subscribe(
      (connectionInfo) => {
        if (connectionInfo && connectionInfo.instance) {
          let context = connectionInfo.instance.name;
          if (connectionInfo.processor) {
            if (authService.getUser()!.hasSystemPrivilege('ReadAlarms')) {
              const options = {
                instance: connectionInfo.instance.name,
                processor: connectionInfo.processor.name,
              };
              this.statusSubscription =
                yamcs.yamcsClient.createGlobalAlarmStatusSubscription(
                  options,
                  (status) => {
                    this.status$.next(status);
                    const alarmCount =
                      status.unacknowledgedCount + status.acknowledgedCount;
                    this.faviconService.showNotification(alarmCount > 0);
                  },
                );
            }
            context += ';' + connectionInfo.processor;
          }
          this.context$.next(context);
        } else {
          this.clearAlarmSubscription();
          this.context$.next(null);
        }
      },
    );
  }

  private clearAlarmSubscription() {
    this.statusSubscription?.cancel();
    this.status$.next(null);
    this.faviconService.showNotification(false);
  }

  ngOnDestroy() {
    this.clearAlarmSubscription();
    this.connectionInfoSubscription?.unsubscribe();
  }
}
