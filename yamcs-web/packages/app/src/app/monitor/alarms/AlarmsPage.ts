import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';

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
export class AlarmsPage implements OnInit {

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
}
