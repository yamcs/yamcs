import { AfterViewInit, ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatPaginator, MatSort, MatTableDataSource } from '@angular/material';
import { Container, Instance } from '@yamcs/client';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { ColumnInfo } from '../../shared/template/ColumnChooser';



@Component({
  selector: 'app-containers-table',
  templateUrl: './ContainersTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainersTable implements AfterViewInit {

  @Input()
  instance: Instance;

  @Input()
  containers$: Promise<Container[]>;

  @Input()
  shortName = false;

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  dataSource = new MatTableDataSource<Container>([]);

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'maxInterval', label: 'Max Interval' },
    { id: 'sizeInBits', label: 'Size in bits' },
    { id: 'baseContainer', label: 'Base Container' },
    { id: 'restrictionCriteria', label: 'Restriction Criteria' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = [
    'name',
    'maxInterval',
    'sizeInBits',
    'baseContainer',
    'restrictionCriteria',
  ];

  constructor(private preferenceStore: PreferenceStore) {
    const cols = preferenceStore.getVisibleColumns('containers');
    if (cols.length) {
      this.displayedColumns = cols;
    }
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.containers$.then(containers => {
      this.dataSource.data = containers;
    });
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('containers', displayedColumns);
  }
}
