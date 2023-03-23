import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatLegacyDialog } from '@angular/material/legacy-dialog';
import { MatLegacyPaginator } from '@angular/material/legacy-paginator';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { MatSort } from '@angular/material/sort';
import { Title } from '@angular/platform-browser';
import { Clearance } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { ChangeLevelDialog } from './ChangeLevelDialog';

@Component({
  templateUrl: './ClearancesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClearancesPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatLegacyPaginator)
  paginator: MatLegacyPaginator;

  displayedColumns = [
    'select',
    'username',
    'hasCommandPrivileges',
    'level',
    'issued',
    'actions',
  ];

  dataSource = new MatLegacyTableDataSource<Clearance>();
  selection = new SelectionModel<Clearance>(true, []);

  constructor(private yamcs: YamcsService, title: Title, private dialog: MatLegacyDialog) {
    title.setTitle('Clearances');
    this.refresh();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.filteredData.length;
    return numSelected === numRows && numRows > 0;
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.filteredData.forEach(row => this.selection.select(row));
  }

  toggleOne(row: Clearance) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  refresh() {
    this.selection.clear();
    this.yamcs.yamcsClient.getClearances().then(page => {
      this.dataSource.data = page.clearances || [];
    });
  }

  isGroupChangeLevelEnabled() {
    return !this.selection.isEmpty();
  }

  openChangeLevelDialog() {
    let clearance: Clearance | undefined;
    if (this.selection.selected.length === 1) {
      clearance = this.selection.selected[0];
    }

    const dialogRef = this.dialog.open(ChangeLevelDialog, {
      data: { clearance },
      width: '400px',
    });
    dialogRef.afterClosed().subscribe(response => {
      if (response) {
        const promises: Array<Promise<any>> = [];
        if (response.level) {
          for (const clearance of this.selection.selected) {
            promises.push(this.yamcs.yamcsClient.changeClearance(clearance.username, {
              level: response.level
            }));
          }
        } else {
          for (const clearance of this.selection.selected) {
            promises.push(this.yamcs.yamcsClient.deleteClearance(clearance.username));
          }
        }
        if (promises.length) {
          Promise.all(promises).then(() => this.refresh());
        }
      }
    });
  }
}
