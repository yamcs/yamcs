import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, Inject, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { UserInfo, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

export interface MemberItem {
  label: string;
  user?: UserInfo;
}


@Component({
  standalone: true,
  selector: 'app-add-members-dialog',
  templateUrl: './add-members-dialog.component.html',
  styleUrl: './add-members-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AddMembersDialogComponent implements AfterViewInit {

  displayedColumns = [
    'select',
    'type',
    'name',
  ];

  filterControl = new UntypedFormControl();

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  dataSource = new MatTableDataSource<MemberItem>();
  selection = new SelectionModel<MemberItem>(true, []);

  constructor(
    private dialogRef: MatDialogRef<AddMembersDialogComponent>,
    yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any
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
