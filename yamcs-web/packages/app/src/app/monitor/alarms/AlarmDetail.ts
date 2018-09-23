import { ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { Alarm, Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { DyDataSource } from '../../shared/widgets/DyDataSource';

@Component({
  selector: 'app-alarm-detail',
  templateUrl: './AlarmDetail.html',
  styleUrls: ['./AlarmDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmDetail implements OnChanges, OnDestroy {

  @Input()
  alarm: Alarm;

  @Input()
  instance: Instance;

  plotDataSource$ = new BehaviorSubject<DyDataSource | null>(null);

  constructor(private yamcs: YamcsService) {
  }

  ngOnChanges() {
    this.disconnectCurrentDataSource();

    const plotDataSource = new DyDataSource(this.yamcs);
    plotDataSource.addParameter(this.alarm.parameter);

    // TODO disabled because plot does not follow selection yet.
    // probably because ParameterPlot should support OnChanges
    // this.plotDataSource$.next(plotDataSource);
  }

  ngOnDestroy() {
    this.disconnectCurrentDataSource();
    this.plotDataSource$.unsubscribe();
  }

  private disconnectCurrentDataSource() {
    const dataSource = this.plotDataSource$.getValue();
    if (dataSource) {
      dataSource.disconnect();
    }
  }
}
