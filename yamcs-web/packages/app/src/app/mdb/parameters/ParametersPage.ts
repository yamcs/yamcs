import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { GetParametersOptions, Instance } from '@yamcs/client';
import { fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { ParametersDataSource } from './ParametersDataSource';

@Component({
  templateUrl: './ParametersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParametersPage implements OnInit, AfterViewInit {

  instance: Instance;
  shortName = false;
  pageSize = 100;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild('filter')
  filter: ElementRef;

  dataSource: ParametersDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'type', label: 'Type' },
    { id: 'units', label: 'Units' },
    { id: 'dataSource', label: 'Data Source' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = [
    'name',
    'type',
    'units',
    'dataSource',
  ];

  constructor(yamcs: YamcsService, title: Title, private preferenceStore: PreferenceStore) {
    title.setTitle('Parameters - Yamcs');
    this.instance = yamcs.getInstance();
    const cols = preferenceStore.getVisibleColumns('parameters');
    if (cols.length) {
      this.displayedColumns = cols;
    }
    this.dataSource = new ParametersDataSource(yamcs);
  }

  ngOnInit() {
    this.dataSource.loadParameters({
      limit: this.pageSize,
    });
  }

  ngAfterViewInit() {
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
    });

    fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(400),
      distinctUntilChanged(),
    ).subscribe(() => this.updateDataSource());
  }

  updateDataSource() {
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

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('parameters', displayedColumns);
  }
}
