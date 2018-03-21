import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Parameter } from '@yamcs/client';
import { ActivatedRoute } from '@angular/router';
import { YamcsService } from '../../core/services/YamcsService';
import { DyDataSource } from '../../shared/widgets/DyDataSource';
import { subtractDuration } from '../../shared/utils';

@Component({
  templateUrl: './ParameterPlotTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterPlotTab {

  parameter$: Promise<Parameter>;
  dataSource: DyDataSource;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.dataSource = new DyDataSource(yamcs, qualifiedName);
    this.parameter$ = yamcs.getSelectedInstance().getParameter(qualifiedName);
  }

  loadLatest(duration: string) {
    const now = new Date(); // TODO use mission time instead
    const start = subtractDuration(now, duration);
    this.dataSource.setDateWindow(start, now);
  }
}
