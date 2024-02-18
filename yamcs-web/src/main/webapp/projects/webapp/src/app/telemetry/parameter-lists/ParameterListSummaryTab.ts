import { ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ColumnChooserComponent, ParameterList, Synchronizer, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { PLIST_COLUMNS, PLIST_SOURCE_OPTIONS, PLIST_TYPE_OPTIONS } from '../parameters/ParametersPage';
import { StreamingParametersDataSource } from './StreamingParametersDataSource';

@Component({
  templateUrl: './ParameterListSummaryTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterListSummaryTab implements OnDestroy {

  filterForm = new UntypedFormGroup({
    filter: new UntypedFormControl(),
    type: new UntypedFormControl('ANY'),
    source: new UntypedFormControl('ANY'),
  });

  plist$ = new BehaviorSubject<ParameterList | null>(null);

  @ViewChild(ColumnChooserComponent)
  columnChooser: ColumnChooserComponent;

  dataSource: StreamingParametersDataSource;

  columns = PLIST_COLUMNS;
  typeOptions = PLIST_TYPE_OPTIONS;
  sourceOptions = PLIST_SOURCE_OPTIONS;

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private synchronizer: Synchronizer,
  ) {
    this.dataSource = new StreamingParametersDataSource(this.yamcs, this.synchronizer);

    route.paramMap.subscribe(params => {
      const plistId = params.get('list')!;
      this.changeList(plistId);
    });
  }

  private changeList(id: string) {
    this.yamcs.yamcsClient.getParameterList(this.yamcs.instance!, id).then(plist => {
      this.plist$.next(plist);
      this.dataSource.loadParameters(plist.match || []);
    });
  }

  ngOnDestroy() {
    this.dataSource?.disconnect();
  }
}
