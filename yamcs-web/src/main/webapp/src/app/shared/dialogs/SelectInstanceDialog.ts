import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatLegacyDialogRef } from '@angular/material/legacy-dialog';
import { MatLegacyPaginator } from '@angular/material/legacy-paginator';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { Instance } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SelectInstanceDialog.html',
  styleUrls: ['./SelectInstanceDialog.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectInstanceDialog implements AfterViewInit {

  filterControl = new UntypedFormControl();

  @ViewChild(MatLegacyPaginator, { static: true })
  paginator: MatLegacyPaginator;

  dataSource = new MatLegacyTableDataSource<Instance>([]);
  selection = new SelectionModel<Instance>();

  displayedColumns = [
    'selected',
    'name',
    'processor',
  ];

  constructor(
    private dialogRef: MatLegacyDialogRef<SelectInstanceDialog>,
    private authService: AuthService,
    readonly yamcs: YamcsService,
    private config: ConfigService,
  ) {
    this.dataSource.filterPredicate = (instance, filter) => {
      return instance.name.toLowerCase().indexOf(filter) >= 0;
    };

    yamcs.yamcsClient.getInstances({
      filter: 'state=running',
    }).then(instances => {
      this.dataSource.data = instances;
    });
  }

  isCreateInstanceEnabled() {
    const user = this.authService.getUser()!;
    return this.config.hasTemplates() && user.hasSystemPrivilege('CreateInstances');
  }

  ngAfterViewInit() {
    this.filterControl.valueChanges.subscribe(() => {
      const value = this.filterControl.value || '';
      this.dataSource.filter = value.toLowerCase();

      if (this.selection.hasValue()) {
        const item = this.selection.selected[0];
        if (this.dataSource.filteredData.indexOf(item) === -1) {
          this.selection.clear();
        }
      }
    });

    this.dataSource.paginator = this.paginator;
  }

  selectNext() {
    const items = this.dataSource.filteredData;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.min(items.indexOf(currentItem) + 1, items.length - 1);
      }
    }
    this.selection.select(items[idx]);
  }

  selectPrevious() {
    const items = this.dataSource.filteredData;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.max(items.indexOf(currentItem) - 1, 0);
      }
    }
    this.selection.select(items[idx]);
  }

  applySelection() {
    const selected = this.selection.selected;
    if (selected.length) {
      const selectedInstance = this.selection.selected[0];
      this.dialogRef.close();
      if (this.yamcs.instance !== selectedInstance.name) {
        this.yamcs.switchContext(selectedInstance.name);
      }
    }
  }
}
