import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { GetAlgorithmsOptions, Instance } from '@yamcs/client';
import { fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { AlgorithmsDataSource } from './AlgorithmsDataSource';

@Component({
  templateUrl: './AlgorithmsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmsPage implements AfterViewInit {

  instance: Instance;
  shortName = false;
  pageSize = 100;

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  @ViewChild('filter', { static: true })
  filter: ElementRef;

  dataSource: AlgorithmsDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'language', label: 'Language' },
    { id: 'scope', label: 'Scope' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = ['name', 'language', 'scope'];

  constructor(
    yamcs: YamcsService,
    title: Title,
    private preferenceStore: PreferenceStore,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Algorithms');
    this.instance = yamcs.getInstance();
    const cols = preferenceStore.getVisibleColumns('algorithms');
    if (cols) {
      this.displayedColumns = cols;
    }
    this.dataSource = new AlgorithmsDataSource(yamcs);
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filter.nativeElement.value = queryParams.get('filter');
    }
    if (queryParams.has('page')) {
      this.paginator.pageIndex = Number(queryParams.get('page'));
    }
    this.updateDataSource();
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
      this.top.nativeElement.scrollIntoView();
    });

    fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(400),
      map(() => this.filter.nativeElement.value.trim()), // Detect 'distinct' on value not on KeyEvent
      distinctUntilChanged(),
    ).subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
    });
  }

  private updateDataSource() {
    this.updateURL();
    const options: GetAlgorithmsOptions = {
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filter.nativeElement.value.trim().toLowerCase();
    if (filterValue) {
      options.q = filterValue;
    }
    this.dataSource.loadAlgorithms(options);
  }

  private updateURL() {
    const filterValue = this.filter.nativeElement.value.trim();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: this.paginator.pageIndex || null,
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('algorithms', displayedColumns);
  }
}
