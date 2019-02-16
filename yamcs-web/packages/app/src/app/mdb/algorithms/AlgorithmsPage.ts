import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { GetAlgorithmsOptions, Instance } from '@yamcs/client';
import { fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { AlgorithmsDataSource } from './AlgorithmsDataSource';

@Component({
  templateUrl: './AlgorithmsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmsPage implements OnInit, AfterViewInit {

  instance: Instance;
  shortName = false;
  pageSize = 100;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  @ViewChild('filter')
  filter: ElementRef;

  dataSource: AlgorithmsDataSource;

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'language', label: 'Language' },
    { id: 'scope', label: 'Scope' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = ['name', 'language', 'scope'];

  constructor(yamcs: YamcsService, title: Title, private preferenceStore: PreferenceStore) {
    title.setTitle('Algorithms - Yamcs');
    this.instance = yamcs.getInstance();
    const cols = preferenceStore.getVisibleColumns('algorithms');
    if (cols.length) {
      this.displayedColumns = cols;
    }
    this.dataSource = new AlgorithmsDataSource(yamcs);
  }

  ngOnInit() {
    this.dataSource.loadAlgorithms({
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

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('algorithms', displayedColumns);
  }
}
