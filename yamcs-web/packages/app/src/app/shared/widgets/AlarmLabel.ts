import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Alarm, Instance, InstanceClient } from '@yamcs/client';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { map } from 'rxjs/operators';
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

  acknowledgedAlarms$: Observable<Alarm[]>;
  unacknowledgedAlarms$: Observable<Alarm[]>;
  hasActiveUnacknowledged$: Observable<boolean>;
  hasActiveAcknowledged$: Observable<boolean>;

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
    this.acknowledgedAlarms$ = this.alarms$.pipe(
      map(alarms => {
        return alarms.filter(alarm => !alarm.shelveInfo && alarm.acknowledged);
      }),
    );
    this.unacknowledgedAlarms$ = this.alarms$.pipe(
      map(alarms => {
        return alarms.filter(alarm => !alarm.shelveInfo && !alarm.acknowledged);
      }),
    );
    this.hasActiveAcknowledged$ = this.acknowledgedAlarms$.pipe(
      map(alarms => {
        for (const alarm of alarms) {
          if (!alarm.processOK) {
            return true;
          }
        }
        return false;
      })
    );
    this.hasActiveUnacknowledged$ = this.unacknowledgedAlarms$.pipe(
      map(alarms => {
        for (const alarm of alarms) {
          if (!alarm.processOK) {
            return true;
          }
        }
        return false;
      })
    );
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
    const normal = alarm.processOK && !alarm.triggered && alarm.acknowledged;
    if (normal || alarm.shelveInfo) {
      delete this.alarmsByName[alarmId];
    } else {
      this.alarmsByName[alarmId] = alarm;
    }
  }

  ngOnDestroy() {
    this.clearAlarmSubscription();
    if (this.connectionInfoSubscription) {
      this.connectionInfoSubscription.unsubscribe();
    }
  }
}
