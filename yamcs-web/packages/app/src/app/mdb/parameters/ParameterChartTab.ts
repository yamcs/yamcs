import { Component, ChangeDetectionStrategy, ViewChild, OnDestroy } from '@angular/core';
import { Parameter } from '@yamcs/client';
import { ActivatedRoute } from '@angular/router';
import { YamcsService } from '../../core/services/YamcsService';
import { DyDataSource } from '../../shared/widgets/DyDataSource';
import { subtractDuration } from '../../shared/utils';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { MatDialog } from '@angular/material';
import { SelectRangeDialog } from './SelectRangeDialog';
import { ParameterPlot } from '../../shared/widgets/ParameterPlot';

@Component({
  templateUrl: './ParameterChartTab.html',
  styleUrls: ['./ParameterChartTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterChartTab implements OnDestroy {

  @ViewChild(ParameterPlot)
  plot: ParameterPlot;

  parameter$: Promise<Parameter>;
  dataSource: DyDataSource;
  missionTime: Date;

  range$ = new BehaviorSubject<string>('PT1H');
  customStart$ = new BehaviorSubject<Date | null>(null);
  customStop$ = new BehaviorSubject<Date | null>(null);

  constructor(route: ActivatedRoute, private yamcs: YamcsService, private dialog: MatDialog) {
    this.missionTime = yamcs.getMissionTime();
    const qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.dataSource = new DyDataSource(yamcs, qualifiedName);
    this.dataSource.connectRealtime();
    this.parameter$ = yamcs.getInstanceClient()!.getParameter(qualifiedName);
  }

  loadLatest(range: string) {
    this.range$.next(range);
    const stop = this.yamcs.getMissionTime();
    const start = subtractDuration(stop, range);

    // Add some padding to the right
    const delta = stop.getTime() - start.getTime();
    stop.setTime(stop.getTime() + 0.05 * delta);

    this.dataSource.updateWindow(start, stop, [null, null]);
  }

  chooseRange() {
    const currentRange = this.plot.getDateRange();
    if (currentRange) {
      const dialogRef = this.dialog.open(SelectRangeDialog, {
        width: '400px',
        data: {
          start: currentRange[0],
          stop: currentRange[1],
        },
      });

      dialogRef.afterClosed().subscribe(result => {
        if (result) {
          this.range$.next('CUSTOM');
          this.customStart$.next(result.start);
          this.customStop$.next(result.stop);
          this.dataSource.updateWindow(result.start, result.stop, [null, null]);
        }
      });
    }
  }

  ngOnDestroy() {
    this.dataSource.disconnect();
  }
}
