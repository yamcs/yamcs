import { Component, ChangeDetectionStrategy, ViewChild } from '@angular/core';
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
export class ParameterChartTab {

  @ViewChild(ParameterPlot)
  plot: ParameterPlot;

  parameter$: Promise<Parameter>;
  dataSource: DyDataSource;

  range$ = new BehaviorSubject<string>('PT1H');
  customStart$ = new BehaviorSubject<Date | null>(null);
  customStop$ = new BehaviorSubject<Date | null>(null);

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
    private dialog: MatDialog,
  ) {
    const qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.dataSource = new DyDataSource(yamcs, qualifiedName);
    this.parameter$ = yamcs.getSelectedInstance().getParameter(qualifiedName);
  }

  loadLatest(range: string) {
    this.range$.next(range);
    const now = new Date(); // TODO use mission time instead
    const start = subtractDuration(now, range);
    this.dataSource.setDateWindow(start, now);
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
          this.dataSource.setDateWindow(result.start, result.stop);
        }
      });
    }
  }
}
