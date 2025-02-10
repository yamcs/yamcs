import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Alarm, BaseComponent, TrackBySelectionModel, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { AlarmsPageTabsComponent } from '../alarms-page-tabs/alarms-page-tabs.component';
import { AlarmsTableComponent } from '../alarms-table/alarm-table.component';
import { AlarmsDataSource } from '../alarms.datasource';

@Component({
  templateUrl: './pending-alarm-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlarmsPageTabsComponent,
    AlarmsTableComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule
  ],
})
export class PendingAlarmListComponent extends BaseComponent implements OnDestroy {

  // Alarm to show in detail pane (only on single selection)
  detailAlarm$ = new BehaviorSubject<Alarm | null>(null);

  dataSource: AlarmsDataSource;
  selection = new TrackBySelectionModel<Alarm>((index: number, alarm: Alarm) => {
    return `${alarm.triggerTime}__${alarm.id.namespace}__${alarm.id.name}__${alarm.seqNum}`;
  }, false, []);

  private alarmsSubscription: Subscription;

  constructor(
    private authService: AuthService,
    readonly yamcs: YamcsService,
  ) {
    super();
    this.setTitle('Pending alarms');

    this.dataSource = new AlarmsDataSource(this.yamcs, true);
    this.dataSource.loadAlarms();

    this.alarmsSubscription = this.dataSource.alarms$.subscribe(alarms => {
      this.selection.matchNewValues(alarms);

      // Update detail pane
      const detailAlarm = this.detailAlarm$.value;
      if (detailAlarm) {
        for (const alarm of alarms) {
          if (this.isSameAlarm(alarm, detailAlarm)) {
            this.detailAlarm$.next(alarm);
            break;
          }
        }
      }
    });
  }

  private isSameAlarm(alarm1: Alarm, alarm2: Alarm) {
    return alarm1.seqNum === alarm2.seqNum
      && alarm1.id.namespace === alarm2.id.namespace
      && alarm1.id.name === alarm2.id.name
      && alarm1.triggerTime === alarm2.triggerTime;
  }

  mayControlAlarms() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlAlarms');
  }

  ngOnDestroy(): void {
    this.alarmsSubscription?.unsubscribe();
  }
}
