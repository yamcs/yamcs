import { CdkColumnDef } from '@angular/cdk/table';
import { AfterViewInit, ChangeDetectionStrategy, Component, QueryList, ViewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { rowAnimation } from '../../animations';
import { YamcsService } from '../../core/services/YamcsService';
import { StreamDataDataSource } from './StreamDataDataSource';

@Component({
  templateUrl: './StreamDataTab.html',
  animations: [rowAnimation],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamDataTab implements AfterViewInit {

  dataSource: StreamDataDataSource;

  availableColumns$: Observable<string[]>;
  displayedColumns: string[] = [];

  @ViewChildren(CdkColumnDef)
  private columnDefinitions: QueryList<CdkColumnDef>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;
    this.dataSource = new StreamDataDataSource(yamcs, name);
    this.availableColumns$ = this.dataSource.columns$;
  }

  // The trick here is to wait until the content children
  // are realised, before attempting to show them via
  // displayedColumns.
  ngAfterViewInit() {
    this.columnDefinitions.changes.subscribe(() => {
      this.columnDefinitions.forEach(def => {
        if (this.displayedColumns.indexOf(def.name) === -1) {
          this.displayedColumns.push(def.name);
        }
      });
    });

    this.dataSource.startStreaming();
  }
}
