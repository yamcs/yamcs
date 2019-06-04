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
        this.setupAlarmSubscription();
      } else {
        this.clearAlarmSubscription();
        this.instance$.next(null);
      }
    });
  }

  private setupAlarmSubscription() {
    this.instanceClient = this.yamcs.getInstanceClient()!;
    this.instanceClient.getAlarmUpdates().then(response => {
      this.alarmSubscription = response.alarm$.subscribe(alarm => {
        this.processAlarm(alarm);
        const alarms = Object.values(this.alarmsByName);
        this.alarms$.next([...alarms]);
      });
    });
  }

  private clearAlarmSubscription() {
    if (this.alarmSubscription) {
      this.alarmSubscription.unsubscribe();
    }
    this.alarms$.next([]);
  }

  private processAlarm(alarm: Alarm) {
    const alarmId = alarm.id.namespace + '/' + alarm.id.name;
    switch (alarm.notificationType) {
      case 'ACTIVE':
      case 'TRIGGERED':
      case 'SEVERITY_INCREASED':
      case 'UPDATED':
      case 'ACKNOWLEDGED':
        this.alarmsByName[alarmId] = alarm;
        break;
      case 'CLEARED':
        delete this.alarmsByName[alarmId];
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
