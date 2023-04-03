import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, Inject, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatLegacyDialogRef, MAT_LEGACY_DIALOG_DATA } from '@angular/material/legacy-dialog';
import { MatLegacyPaginator } from '@angular/material/legacy-paginator';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { UserInfo } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

export interface MemberItem {
  label: string;
  user?: UserInfo;
}

@Component({
  selector: 'app-add-members-dialog',
  templateUrl: './AddMembersDialog.html',
  styleUrls: ['./AddMembersDialog.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddMembersDialog implements AfterViewInit {

  displayedColumns = [
    'select',
    'type',
    'name',
  ];

  filterControl = new UntypedFormControl();

  @ViewChild(MatLegacyPaginator, { static: true })
  paginator: MatLegacyPaginator;

  dataSource = new MatLegacyTableDataSource<MemberItem>();
  selection = new SelectionModel<MemberItem>(true, []);

  constructor(
    private dialogRef: MatLegacyDialogRef<AddMembersDialog>,
    yamcs: YamcsService,
    @Inject(MAT_LEGACY_DIALOG_DATA) readonly data: any
  ) {
    const existingItems: MemberItem[] = data.items;
    const existingUsernames = existingItems.filter(i => i.user).map(i => i.user!.name);
    yamcs.yamcsClient.getUsers().then(users => {
      const items = (users || []).filter(user => existingUsernames.indexOf(user.name) === -1).map(user => {
        return {
          label: user.displayName || user.name,
          user,
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

  toggleOne(row: MemberItem) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  save() {
    this.dialogRef.close(this.selection.selected);
  }
}
