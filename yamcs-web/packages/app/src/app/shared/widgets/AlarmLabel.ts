import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Alarm, Instance, InstanceClient } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-alarm-label',
  templateUrl: './AlarmLabel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmLabel implements OnDestroy {

  private connectionInfoSubscription: Subscription;

  instance$ = new BehaviorSubject<Instance | null>(null);
  alarms$ = new BehaviorSubject<Alarm[]>([]);

  private alarmsByName: { [key: string]: Alarm } = {};

  private instanceClient: InstanceClient;
  private alarmSubscription: Subscription;

  constructor(private yamcs: YamcsService) {
    this.connectionInfoSubscription = yamcs.connectionInfo$.subscribe(connectionInfo => {
      if (connectionInfo && connectionInfo.instance) {
        if (this.instanceClient && this.instanceClient.instance !== connectionInfo.instance.name) {
          this.clearAlarmSubscription();
        }
        this.instance$.next(connectionInfo.instance);
        this.setupAlarmSubscription(connectionInfo.instance.name);
      } else {
        this.clearAlarmSubscription();
        this.instance$.next(null);
      }
    });
  }

  private setupAlarmSubscription(instance: string) {
    this.instanceClient = this.yamcs.yamcsClient.createInstanceClient(instance);
    this.instanceClient.getAlarmUpdates().then(response => {
      this.alarmSubscription = response.alarm$.subscribe(alarm => {
        this.processAlarm(alarm);
        const alarms = Object.values(this.alarmsByName);
        this.alarms$.next([... alarms]);
      });
    });
  }

  private clearAlarmSubscription() {
    if (this.alarmSubscription) {
      this.alarmSubscription.unsubscribe();
    }
    if (this.instanceClient) {
      this.instanceClient.closeConnection();
    }
    this.alarms$.next([]);
  }

  private processAlarm(alarm: Alarm) {
    switch (alarm.type) {
      case 'ACTIVE':
      case 'TRIGGERED':
      case 'SEVERITY_INCREASED':
      case 'PVAL_UPDATED':
      case 'ACKNOWLEDGED':
        this.alarmsByName[alarm.triggerValue.id.name] = alarm;
        break;
      case 'CLEARED':
        delete this.alarmsByName[alarm.triggerValue.id.name];
        break;
      default:
        console.warn('Unexpected alarm event of type', alarm.type);
    }
  }

  ngOnDestroy() {
    this.clearAlarmSubscription();
    if (this.connectionInfoSubscription) {
      this.connectionInfoSubscription.unsubscribe();
    }
  }
}
