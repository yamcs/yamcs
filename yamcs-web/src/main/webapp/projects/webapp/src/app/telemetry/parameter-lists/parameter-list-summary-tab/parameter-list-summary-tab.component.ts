import { ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ParameterList, Synchronizer, WebappSdkModule, YaColumnChooser, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { PLIST_COLUMNS } from '../../parameters/parameters/parameters.component';
import { StreamingParametersDataSource } from './streaming-parameters.datasource';

@Component({
  standalone: true,
  templateUrl: './parameter-list-summary-tab.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ParameterListSummaryTabComponent implements OnDestroy {

  plist$ = new BehaviorSubject<ParameterList | null>(null);

  @ViewChild(YaColumnChooser)
  columnChooser: YaColumnChooser;

  dataSource: StreamingParametersDataSource;

  columns = PLIST_COLUMNS;

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
