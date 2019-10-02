import { CdkColumnDef } from '@angular/cdk/table';
import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, QueryList, ViewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StreamData } from '@yamcs/client';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { rowAnimation } from '../../animations';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { StreamDataDataSource } from './StreamDataDataSource';

@Component({
  templateUrl: './StreamDataTab.html',
  styleUrls: ['./StreamDataTab.css'],
  animations: [rowAnimation],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamDataTab implements AfterViewInit, OnDestroy {

  dataSource: StreamDataDataSource;

  availableColumns$: Observable<string[]>;
  displayedColumns: string[] = [];

  @ViewChildren(CdkColumnDef)
  private columnDefinitions: QueryList<CdkColumnDef>;
  private columnDefinitionsSubscription: Subscription;

  selectedStreamData$ = new BehaviorSubject<StreamData | null>(null);

  constructor(route: ActivatedRoute, yamcs: YamcsService, synchronizer: Synchronizer) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;
    this.dataSource = new StreamDataDataSource(yamcs, synchronizer, name);
    this.availableColumns$ = this.dataSource.columns$;
  }

  // The trick here is to wait until the content children
  // are realised, before attempting to show them via
  // displayedColumns.
  ngAfterViewInit() {
    this.columnDefinitionsSubscription = this.columnDefinitions.changes.subscribe(() => {
      this.columnDefinitions.forEach(def => {
        if (this.displayedColumns.indexOf(def.name) === -1) {
          this.displayedColumns.push(def.name);
        }
      });
    });

    this.dataSource.startStreaming();
  }

  selectStreamData(streamData: StreamData) {
    this.selectedStreamData$.next(streamData);
  }

  ngOnDestroy() {
    if (this.columnDefinitionsSubscription) {
      this.columnDefinitionsSubscription.unsubscribe();
    }
    if (this.dataSource) {
      this.dataSource.stopStreaming();
    }
  }
}
