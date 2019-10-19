import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { GetParametersOptions, Instance } from '@yamcs/client';
import { fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { ParametersDataSource } from './ParametersDataSource';

@Component({
  templateUrl: './ParametersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPage implements AfterViewInit {

  instance: Instance;
  shortName = false;
  pageSize = 100;

  @ViewChild('top', { static: true })
  top: ElementRef;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  @ViewChild('filter', { static: true })
  filter: ElementRef;

  dataSource: ParametersDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'rawValue', label: 'Raw Value' },
    { id: 'engValue', label: 'Value' },
    { id: 'dataSource', label: 'Data Source' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = [
    'name',
    'engValue',
    'dataSource',
  ];

  constructor(
    yamcs: YamcsService,
    title: Title,
    private preferenceStore: PreferenceStore,
    private route: ActivatedRoute,
    private router: Router,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('Parameters');
    this.instance = yamcs.getInstance();
    const cols = preferenceStore.getVisibleColumns('parameters');
    if (cols) {
      this.displayedColumns = cols;
    }
    this.dataSource = new ParametersDataSource(yamcs, synchronizer);
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
    const options: GetParametersOptions = {
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filter.nativeElement.value.trim().toLowerCase();
    if (filterValue) {
      options.q = filterValue;
    }
    this.dataSource.loadParameters(options);
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
    this.preferenceStore.setVisibleColumns('parameters', displayedColumns);
  }
}
