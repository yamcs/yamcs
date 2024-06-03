import { CdkColumnDef } from '@angular/cdk/table';
import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, QueryList, ViewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StreamData, Synchronizer, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { ColumnValuePipe } from '../shared/column-value.pipe';
import { StreamDataComponent } from '../stream-data/stream-data.component';
import { StreamDataDataSource } from './stream-data.datasource';

@Component({
  standalone: true,
  templateUrl: './stream-data-tab.component.html',
  styleUrl: './stream-data-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ColumnValuePipe,
    WebappSdkModule,
    StreamDataComponent,
  ],
})
export class StreamDataTabComponent implements AfterViewInit, OnDestroy {

  dataSource: StreamDataDataSource;

  availableColumns$: Observable<string[]>;
  displayedColumns = ['actions'];

  @ViewChildren(CdkColumnDef)
  private columnDefinitions: QueryList<CdkColumnDef>;
  private columnDefinitionsSubscription: Subscription;

  selectedStreamData$ = new BehaviorSubject<StreamData | null>(null);

  constructor(route: ActivatedRoute, yamcs: YamcsService, synchronizer: Synchronizer) {
    const parent = route.snapshot.parent!;
    const database = parent.parent!.paramMap.get('database')!;
    const name = parent.paramMap.get('stream')!;
    this.dataSource = new StreamDataDataSource(yamcs, synchronizer, database, name);
    this.availableColumns$ = this.dataSource.columns$;
  }

  // The trick here is to wait until the content children
  // are realised, before attempting to show them via
  // displayedColumns.
  ngAfterViewInit() {
    this.columnDefinitionsSubscription = this.columnDefinitions.changes.subscribe(() => {
      this.columnDefinitions.forEach(def => {
        if (this.displayedColumns.indexOf(def.name) === -1) {
          this.displayedColumns.splice(this.displayedColumns.length - 1, 0, def.name);
        }
      });
    });

    this.dataSource.startStreaming();
  }

  selectStreamData(streamData: StreamData) {
    this.selectedStreamData$.next(streamData);
  }

  ngOnDestroy() {
    this.columnDefinitionsSubscription?.unsubscribe();
    this.dataSource?.stopStreaming();
  }
}
