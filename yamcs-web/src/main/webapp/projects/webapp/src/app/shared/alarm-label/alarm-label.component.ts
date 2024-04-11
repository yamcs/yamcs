import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { FaviconService, GlobalAlarmStatus, GlobalAlarmStatusSubscription, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';

@Component({
  standalone: true,
  selector: 'app-alarm-label',
  templateUrl: './alarm-label.component.html',
  styleUrl: './alarm-label.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AlarmLabelComponent implements OnDestroy {

  private connectionInfoSubscription: Subscription;

  context$ = new BehaviorSubject<string | null>(null);
  status$ = new BehaviorSubject<GlobalAlarmStatus | null>(null);

  private statusSubscription: GlobalAlarmStatusSubscription;

  constructor(
    readonly yamcs: YamcsService,
    readonly faviconService: FaviconService,
    authService: AuthService,
  ) {
    this.connectionInfoSubscription = yamcs.connectionInfo$.subscribe(connectionInfo => {
      if (connectionInfo && connectionInfo.instance) {
        /*if (this.instanceClient && this.instanceClient.instance !== connectionInfo.instance) {
          this.clearAlarmSubscription();
        }*/
        let context = connectionInfo.instance.name;
        if (connectionInfo.processor) {
          if (authService.getUser()!.hasSystemPrivilege('ReadAlarms')) {
            const options = {
              instance: connectionInfo.instance.name,
              processor: connectionInfo.processor.name,
            };
            this.statusSubscription = yamcs.yamcsClient.createGlobalAlarmStatusSubscription(options, status => {
              this.status$.next(status);
              const alarmCount = status.unacknowledgedCount + status.acknowledgedCount;
              this.faviconService.showNotification(alarmCount > 0);
            });
          }
          context += ';' + connectionInfo.processor;
        }
        this.context$.next(context);
      } else {
        this.clearAlarmSubscription();
        this.context$.next(null);
      }
    });
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
