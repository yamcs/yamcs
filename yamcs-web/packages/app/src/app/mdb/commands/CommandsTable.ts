import { AfterViewInit, ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatPaginator, MatSort, MatTableDataSource } from '@angular/material';
import { Command, Instance } from '@yamcs/client';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { ColumnInfo } from '../../shared/template/ColumnChooser';



@Component({
  selector: 'app-commands-table',
  templateUrl: './CommandsTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandsTable implements AfterViewInit {

  @Input()
  instance: Instance;

  @Input()
  commands$: Promise<Command[]>;

  @Input()
  shortName = false;

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  dataSource = new MatTableDataSource<Command>([]);

  columns: ColumnInfo[] = [
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'abstract', label: 'Abstract' },
    { id: 'shortDescription', label: 'Description' },
  ];

  displayedColumns = [
    'name',
    'abstract',
  ];

  constructor(private preferenceStore: PreferenceStore) {
    const cols = preferenceStore.getVisibleColumns('commands');
    if (cols.length) {
      this.displayedColumns = cols;
    }
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.commands$.then(commands => {
      this.dataSource.data = commands;
    });
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }

  updateColumns(displayedColumns: string[]) {
    this.displayedColumns = displayedColumns;
    this.preferenceStore.setVisibleColumns('commands', displayedColumns);
  }
}
