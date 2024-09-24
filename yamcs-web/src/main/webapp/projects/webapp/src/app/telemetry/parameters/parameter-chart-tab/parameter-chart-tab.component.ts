import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { BackfillingSubscription, ConfigService, Parameter, Synchronizer, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { DyDataSource } from '../../../shared/parameter-plot/DyDataSource';
import { ParameterPlotComponent } from '../../../shared/parameter-plot/parameter-plot.component';
import { ParameterSeriesComponent } from '../../../shared/parameter-plot/parameter-series/parameter-series.component';
import { CompareParameterDialogComponent } from '../compare-parameter-dialog/compare-parameter-dialog.component';
import { SelectRangeDialogComponent } from '../select-range-dialog/select-range-dialog.component';

@Component({
  standalone: true,
  templateUrl: './parameter-chart-tab.component.html',
  styleUrl: './parameter-chart-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ParameterPlotComponent,
    ParameterSeriesComponent,
    WebappSdkModule,
  ],
})
export class ParameterChartTabComponent implements OnInit, OnDestroy {

  qualifiedName = input.required<string>({ alias: 'parameter' });

  @ViewChild(ParameterPlotComponent)
  plot: ParameterPlotComponent;

  parameter$: Promise<Parameter>;
  dataSource: DyDataSource;
  missionTime: Date;
  private timeSubscription: Subscription;
  private backfillSubscription: BackfillingSubscription;

  range$ = new BehaviorSubject<string>('PT15M');
  customStart$ = new BehaviorSubject<Date | null>(null);
  customStop$ = new BehaviorSubject<Date | null>(null);

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
    private synchronizer: Synchronizer,
    private configService: ConfigService,
  ) { }

  ngOnInit() {
    this.missionTime = this.yamcs.getMissionTime();
    this.initializeOptions();

    const qualifiedName = this.qualifiedName();
    this.dataSource = new DyDataSource(this.yamcs, this.synchronizer, this.configService);
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

      this.backfillSubscription = this.yamcs.yamcsClient.createBackfillingSubscription({
        instance: this.yamcs.instance!
      }, update => {
        if (update.finished) {
          this.dataSource.reloadVisibleRange();
        }
      });
    });
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
      const dialogRef = this.dialog.open(SelectRangeDialogComponent, {
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
    const dialogRef = this.dialog.open(CompareParameterDialogComponent, {
      width: '600px',
      data: {
        exclude: this.plot.getParameters(),
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.yamcs.yamcsClient.getParameter(this.yamcs.instance!, result.qualifiedName).then(parameter => {
          const parameterConfig = new ParameterSeriesComponent();
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
    this.backfillSubscription?.cancel();
    this.timeSubscription?.unsubscribe();
    this.dataSource?.disconnect();
  }
}
