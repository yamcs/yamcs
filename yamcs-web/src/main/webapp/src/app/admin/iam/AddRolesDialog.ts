import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, Inject, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { RoleInfo } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

export interface RoleItem {
  label: string;
  role?: RoleInfo;
}

@Component({
  selector: 'app-add-roles-dialog',
  templateUrl: './AddRolesDialog.html',
  styleUrls: ['./AddRolesDialog.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddRolesDialog implements AfterViewInit {

  displayedColumns = [
    'select',
    'name',
  ];

  filterControl = new FormControl();

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  dataSource = new MatTableDataSource<RoleItem>();
  selection = new SelectionModel<RoleItem>(true, []);

  constructor(
    private dialogRef: MatDialogRef<AddRolesDialog>,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any
  ) {
    const existingItems: RoleItem[] = data.items;
    const existingRoles = existingItems.filter(i => i.role).map(i => i.role!.name);
    yamcs.yamcsClient.getRoles().then(roles => {
      const items = (roles || []).filter(role => existingRoles.indexOf(role.name) === -1).map(role => {
        return {
          label: role.name,
          role,
        };
      });
      this.dataSource.data = items;
    });
  }

  ngAfterViewInit() {
    this.filterControl.valueChanges.subscribe(() => {
      const value = this.filterControl.value || '';
      this.dataSource.filter = value.toLowerCase();
    });
    this.dataSource.filterPredicate = (member, filter) => {
      return member.label.toLowerCase().indexOf(filter) >= 0;
    };
    this.dataSource.paginator = this.paginator;
  }

  toggleOne(row: RoleItem) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  save() {
    this.dialogRef.close(this.selection.selected);
  }
}
