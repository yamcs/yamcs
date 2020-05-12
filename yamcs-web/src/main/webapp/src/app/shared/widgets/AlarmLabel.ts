import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { GlobalAlarmStatus, GlobalAlarmStatusSubscription } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-alarm-label',
  templateUrl: './AlarmLabel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmLabel implements OnDestroy {

  private connectionInfoSubscription: Subscription;

  context$ = new BehaviorSubject<string | null>(null);
  status$ = new BehaviorSubject<GlobalAlarmStatus | null>(null);

  private statusSubscription: GlobalAlarmStatusSubscription;

  constructor(readonly yamcs: YamcsService) {
    this.connectionInfoSubscription = yamcs.connectionInfo$.subscribe(connectionInfo => {
      if (connectionInfo && connectionInfo.instance) {
        /*if (this.instanceClient && this.instanceClient.instance !== connectionInfo.instance) {
          this.clearAlarmSubscription();
        }*/
        let context = connectionInfo.instance;
        if (connectionInfo.processor) {
          const options = {
            instance: connectionInfo.instance,
            processor: connectionInfo.processor.name,
          };
          this.statusSubscription = yamcs.yamcsClient.createGlobalAlarmStatusSubscription(options, status => {
            this.status$.next(status);
          });
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
    if (this.statusSubscription) {
      this.statusSubscription.cancel();
    }
    this.status$.next(null);
  }

  ngOnDestroy() {
    this.clearAlarmSubscription();
    if (this.connectionInfoSubscription) {
      this.connectionInfoSubscription.unsubscribe();
    }
  }
}
