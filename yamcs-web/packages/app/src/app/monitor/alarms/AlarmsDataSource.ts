import { DataSource } from '@angular/cdk/table';
import { Alarm, MonitoringResult } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../../shared/utils';

export class AlarmsDataSource extends DataSource<Alarm> {

  alarms$ = new BehaviorSubject<Alarm[]>([]);
  loading$ = new BehaviorSubject<boolean>(false);

  alarmSubscription: Subscription;

  private alarmsByName: { [key: string]: Alarm } = {};
  private sortColumn = 'parameter';
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
      if (this.sortColumn === 'parameter') {
        rc = a1.parameter.qualifiedName.localeCompare(a2.parameter.qualifiedName);
      } else if (this.sortColumn === 'severity') {
        const m1 = this.getNumericMonitoringResult(a1.triggerValue.monitoringResult);
        const m2 = this.getNumericMonitoringResult(a2.triggerValue.monitoringResult);
        rc = (m1 < m2) ? -1 : ((m1 === m2) ? 0 : 1);
      } else if (this.sortColumn === 'violations') {
        rc = (a1.violations < a2.violations) ? -1 : ((a1.violations === a2.violations) ? 0 : 1);
      } else if (this.sortColumn === 'time') {
        rc = a1.triggerValue.generationTimeUTC.localeCompare(a2.triggerValue.generationTimeUTC);
      } else if (this.sortColumn === 'currentValue') {
        const v1 = utils.printValue(a1.currentValue.engValue);
        const v2 = utils.printValue(a2.currentValue.engValue);
        rc = v1.localeCompare(v2);
      } else if (this.sortColumn === 'value') {
        const v1 = utils.printValue(a1.triggerValue.engValue);
        const v2 = utils.printValue(a2.triggerValue.engValue);
        rc = v1.localeCompare(v2);
      }
      return this.sortDirection === 'asc' ? rc : -rc;
    });
    this.alarms$.next([... alarms]);
  }

  private getNumericMonitoringResult(monitoringResult: MonitoringResult) {
    switch (monitoringResult) {
      case 'DISABLED': return 0;
      case 'IN_LIMITS': return 1;
      case 'WATCH': return 2;
      case 'WARNING': return 3;
      case 'DISTRESS': return 4;
      case 'CRITICAL': return 5;
      case 'SEVERE': return 6;
      default: return 7;
    }
  }

  disconnect() {
    this.alarms$.complete();
    this.loading$.complete();
    if (this.alarmSubscription) {
      this.alarmSubscription.unsubscribe();
    }
  }

  isEmpty() {
    return !this.alarms$.getValue().length;
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
}
