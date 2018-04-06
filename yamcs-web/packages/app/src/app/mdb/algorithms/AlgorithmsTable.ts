import { Component, ChangeDetectionStrategy, ViewChild, Input, AfterViewInit } from '@angular/core';

import { Algorithm } from '@yamcs/client';

import { MatSort, MatTableDataSource, MatPaginator } from '@angular/material';
import { ColumnInfo } from '../../shared/template/ColumnChooser';
import { PreferenceStore } from '../../core/services/PreferenceStore';

@Component({
  selector: 'app-algorithms-table',
  templateUrl: './AlgorithmsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmsTable implements AfterViewInit {

  @Input()
  instance: string;

  @Input()
  algorithms$: Promise<Algorithm[]>;

  @Input()
  shortName = false;

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  dataSource = new MatTableDataSource<Algorithm>([]);

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'language', label: 'Language' },
    { id: 'scope', label: 'Scope' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = ['name', 'language', 'scope'];

  constructor(private preferenceStore: PreferenceStore) {
    const cols = preferenceStore.getVisibleColumns('algorithms');
    if (cols.length) {
      this.displayedColumns = cols;
    }
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.algorithms$.then(algorithms => {
      this.dataSource.data = algorithms;
    });
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('algorithms', displayedColumns);
  }
}
