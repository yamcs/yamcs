import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { Clearance, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { SignificanceLevelComponent } from '../../../shared/significance-level/significance-level.component';
import { ChangeLevelDialogComponent } from '../change-level-dialog/change-level-dialog.component';
import { ClearancesPageTabsComponent } from '../clearances-page-tabs/clearances-page-tabs.component';

@Component({
  standalone: true,
  templateUrl: './clearances-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ClearancesPageTabsComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
    SignificanceLevelComponent,
  ],
})
export class ClearancesListComponent implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  displayedColumns = [
    'select',
    'username',
    'hasCommandPrivileges',
    'level',
    'issued',
    'actions',
  ];

  dataSource = new MatTableDataSource<Clearance>();
  selection = new SelectionModel<Clearance>(true, []);

  constructor(private yamcs: YamcsService, title: Title, private dialog: MatDialog) {
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

    const dialogRef = this.dialog.open(ChangeLevelDialogComponent, {
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
