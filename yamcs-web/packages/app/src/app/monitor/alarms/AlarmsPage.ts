import { Component, ChangeDetectionStrategy, OnInit, OnDestroy } from '@angular/core';

import { Alarm, Instance } from '@yamcs/client';

import { YamcsService } from '../../core/services/YamcsService';
import { AlarmsDataSource } from './AlarmsDataSource';
import { BehaviorSubject } from 'rxjs';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './AlarmsPage.html',
  styleUrls: ['./AlarmsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmsPage implements OnInit, OnDestroy {

  instance: Instance;

  selectedAlarm$ = new BehaviorSubject<Alarm | null>(null);

  displayedColumns = [
    'severity',
    'parameter',
    'time',
    'value',
    'type',
    'currentValue',
    'violations'
  ];

  dataSource: AlarmsDataSource;

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Alarms - Yamcs');
    this.instance = this.yamcs.getInstance();
  }

  // Used in table trackBy to prevent continuous row recreation
  tableTrackerFn = (index: number, alarm: Alarm) => alarm.triggerValue.id.name;

  ngOnInit() {
    this.dataSource = new AlarmsDataSource(this.yamcs);
    this.dataSource.loadAlarms('realtime');
  }

  selectAlarm(alarm: Alarm) {
    this.selectedAlarm$.next(alarm);
  }

  onAcknowledge(alarm: Alarm) {
    this.selectedAlarm$.next(null);
    this.yamcs.getInstanceClient()!.acknowledgeAlarm('realtime', alarm.parameter.qualifiedName, alarm.seqNum);
  }

  onClear(alarm: Alarm) {
    this.selectedAlarm$.next(null);
    this.yamcs.getInstanceClient()!.clearAlarm('realtime', alarm.parameter.qualifiedName, alarm.seqNum);
  }

  ngOnDestroy() {
    this.dataSource.destroy();
  }
}
