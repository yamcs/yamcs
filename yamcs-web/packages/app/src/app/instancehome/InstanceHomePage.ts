import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Alarm, GeneralInfo, Instance, MissionDatabase, TmStatistics } from '@yamcs/client';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { YamcsService } from '../core/services/YamcsService';
import { AlarmsDataSource } from '../monitor/alarms/AlarmsDataSource';

@Component({
  templateUrl: './InstanceHomePage.html',
  styleUrls: ['./InstanceHomePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstanceHomePage implements OnDestroy {

  instance: Instance;

  tmstats$ = new BehaviorSubject<TmStatistics[]>([]);
  tmstatsSubscription: Subscription;

  unacknowledgedAlarms$: Observable<Alarm[]>;
  alarmsDataSource: AlarmsDataSource;

  info$: Promise<GeneralInfo>;
  mdb$: Promise<MissionDatabase>;

  constructor(yamcs: YamcsService) {
    const processor = yamcs.getProcessor();
    this.instance = yamcs.getInstance();
    yamcs.getInstanceClient()!.getProcessorStatistics().then(response => {
      response.statistics$.pipe(
        filter(stats => stats.yProcessorName === processor.name),
        map(stats => stats.tmstats || []),
      ).subscribe(tmstats => {
        this.tmstats$.next(tmstats);
      });
    });

    this.alarmsDataSource = new AlarmsDataSource(yamcs);
    this.alarmsDataSource.loadAlarms('realtime');
    this.unacknowledgedAlarms$ = this.alarmsDataSource.alarms$.pipe(
      map(alarms => {
        return alarms.filter(alarm => alarm.type !== 'CLEARED' && alarm.type !== 'ACKNOWLEDGED');
      }),
    );

    this.mdb$ = yamcs.getInstanceClient()!.getMissionDatabase();

    this.info$ = yamcs.yamcsClient.getGeneralInfo();
  }

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.unsubscribe();
    }
    if (this.alarmsDataSource) {
      this.alarmsDataSource.disconnect();
    }
  }
}
