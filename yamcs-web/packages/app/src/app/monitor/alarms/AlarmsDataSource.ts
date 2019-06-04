import { DataSource } from '@angular/cdk/table';
import { Alarm, AlarmSeverity } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

export class AlarmsDataSource extends DataSource<Alarm> {

  alarms$ = new BehaviorSubject<Alarm[]>([]);
  loading$ = new BehaviorSubject<boolean>(false);

  alarmSubscription: Subscription;

  private alarmsByName: { [key: string]: Alarm } = {};
  private sortColumn = 'source';
  private sortDirection = 'asc';

  constructor(private yamcs: YamcsService) {
    super();
  }

  connect() {
    return this.alarms$;
  }

  loadAlarms(processorName: string) {
    this.loading$.next(true);
    this.yamcs.getInstanceClient()!.getActiveAlarms(processorName)
      .then(alarms => {
        this.loading$.next(false);
        for (const alarm of alarms) {
          this.processAlarm(alarm);
        }
        this.updateSubject();
      });

    this.yamcs.getInstanceClient()!.getAlarmUpdates().then(response => {
      this.alarmSubscription = response.alarm$.subscribe(alarm => {
        this.processAlarm(alarm);
        this.updateSubject();
      });
    });
  }

  setSort(sortColumn: string, sortDirection: string) {
    this.sortColumn = sortColumn;
    this.sortDirection = sortDirection;
    this.updateSubject();
  }

  private updateSubject() {
    const alarms = Object.values(this.alarmsByName);
    alarms.sort((a1, a2) => {
      let rc = 0;
      if (this.sortColumn === 'source') {
        const id1 = a1.id.namespace + '/' + a1.id.name;
        const id2 = a2.id.namespace + '/' + a2.id.name;
        rc = id1.localeCompare(id2);
      } else if (this.sortColumn === 'severity') {
        const m1 = this.getNumericSeverity(a1.severity);
        const m2 = this.getNumericSeverity(a2.severity);
        rc = (m1 < m2) ? -1 : ((m1 === m2) ? 0 : 1);
      } else if (this.sortColumn === 'violations') {
        rc = (a1.violations < a2.violations) ? -1 : ((a1.violations === a2.violations) ? 0 : 1);
      } else if (this.sortColumn === 'time') {
        rc = a1.triggerTime.localeCompare(a2.triggerTime);
      }
      return this.sortDirection === 'asc' ? rc : -rc;
    });
    this.alarms$.next([... alarms]);
  }

  private getNumericSeverity(severity: AlarmSeverity) {
    switch (severity) {
      case 'WATCH': return 0;
      case 'WARNING': return 1;
      case 'DISTRESS': return 2;
      case 'CRITICAL': return 3;
      case 'SEVERE': return 4;
      default: return 5;
    }
  }

  disconnect() {
    this.alarms$.complete();
    this.loading$.complete();
    if (this.alarmSubscription) {
      this.alarmSubscription.unsubscribe();
    }
  }

  getUnacknowledgedCount() {
    let total = 0;
    for (const alarm of this.alarms$.getValue()) {
      if (alarm.notificationType !== 'CLEARED' && alarm.notificationType !== 'ACKNOWLEDGED') {
        total++;
      }
    }
    return total;
  }

  getActiveAlarmCount() {
    return this.alarms$.getValue().length;
  }

  isEmpty() {
    return !this.alarms$.getValue().length;
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
        console.warn('Unexpected alarm event of type', alarm.notificationType);
    }
  }
}
