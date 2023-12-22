import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { Parameter, Synchronizer, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { DyDataSource } from '../../shared/widgets/DyDataSource';
import { ParameterPlot } from '../../shared/widgets/ParameterPlot';
import { ParameterSeries } from '../../shared/widgets/ParameterSeries';
import { CompareParameterDialog } from './CompareParameterDialog';
import { SelectRangeDialog } from './SelectRangeDialog';

@Component({
  templateUrl: './ParameterChartTab.html',
  styleUrls: ['./ParameterChartTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterChartTab implements AfterViewInit, OnDestroy {

  @ViewChild(ParameterPlot)
  plot: ParameterPlot;

  parameter$: Promise<Parameter>;
  dataSource: DyDataSource;
  missionTime: Date;
  private timeSubscription: Subscription;

  range$ = new BehaviorSubject<string>('PT15M');
  customStart$ = new BehaviorSubject<Date | null>(null);
  customStop$ = new BehaviorSubject<Date | null>(null);

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
    private synchronizer: Synchronizer,
  ) {
    this.missionTime = yamcs.getMissionTime();
    this.initializeOptions();

    const qualifiedName = this.route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.dataSource = new DyDataSource(this.yamcs, this.synchronizer);
    this.parameter$ = this.yamcs.yamcsClient.getParameter(this.yamcs.instance!, qualifiedName);
    this.parameter$.then(parameter => {
      // Override qualified name for possible array or aggregate offsets
      parameter.qualifiedName = qualifiedName;
      this.dataSource.addParameter(parameter);

      const interval = this.range$.value;
      if (interval === 'CUSTOM') {
        const start = this.customStart$.value!;
        const stop = this.customStop$.value!;
        this.dataSource.updateWindow(start, stop, [null, null]);
      } else {
        const stop = this.yamcs.getMissionTime();
        const start = utils.subtractDuration(stop, this.range$.value);
        this.dataSource.updateWindow(start, stop, [null, null]);
      }

      // Autoscroll (don't care about data, that is triggered by plot buffer)
      this.timeSubscription = this.yamcs.time$.subscribe(() => {
        if (this.range$.value !== 'CUSTOM') {
          const stop = this.yamcs.getMissionTime();
          const start = utils.subtractDuration(stop, this.range$.value);
          this.plot?.updateWindowOnly(start, stop);
        }
      });
    });
  }

  ngAfterViewInit() {
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('interval')) {
      this.range$.next(queryParams.get('interval')!);
      if (queryParams.get('interval') === 'CUSTOM') {
        this.customStart$.next(new Date(queryParams.get('customStart')!));
        this.customStop$.next(new Date(queryParams.get('customStop')!));
      }
    }
    this.updateURL();
  }

  onVisibleRange(xRange: [Date, Date]) {
    this.customStart$.next(xRange[0]);
    this.customStop$.next(xRange[1]);
  }

  onManualRangeChange() {
    this.range$.next('CUSTOM');
  }

  loadLatest(range: string) {
    this.range$.next(range);
    const stop = this.yamcs.getMissionTime();
    const start = utils.subtractDuration(stop, range);
    this.updateURL();
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
          this.updateURL();
          this.dataSource.updateWindow(result.start, result.stop, [null, null]);
        }
      });
    }
  }

  compareParameter() {
    const dialogRef = this.dialog.open(CompareParameterDialog, {
      width: '600px',
      data: {
        exclude: this.plot.getParameters(),
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.yamcs.yamcsClient.getParameter(this.yamcs.instance!, result.qualifiedName).then(parameter => {
          const parameterConfig = new ParameterSeries();
          parameterConfig.parameter = result.qualifiedName;
          parameterConfig.color = result.color;
          parameterConfig.strokeWidth = result.thickness;
          // Override qualified name for possible array or aggregate offsets
          parameter.qualifiedName = result.qualifiedName;
          this.plot.addParameter(parameter, parameterConfig);
        });
      }
    });
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        interval: this.range$.value,
        customStart: this.range$.value === 'CUSTOM' ? this.customStart$.value : null,
        customStop: this.range$.value === 'CUSTOM' ? this.customStop$.value : null,
      },
      queryParamsHandling: 'merge',
    });
  }

  ngOnDestroy() {
    if (this.timeSubscription) {
      this.timeSubscription.unsubscribe();
    }
    this.dataSource.disconnect();
  }
}
