import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, Inject, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { ArgumentType, EnumValue, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-select-enumeration-dialog',
  templateUrl: './select-enumeration-dialog.component.html',
  styleUrl: './select-enumeration-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class SelectEnumerationDialogComponent implements AfterViewInit {

  filterControl = new UntypedFormControl();

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  dataSource = new MatTableDataSource<EnumValue>([]);
  selection = new SelectionModel<EnumValue>();

  displayedColumns = [
    'name',
    'value',
  ];

  constructor(
    private dialogRef: MatDialogRef<SelectEnumerationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    const argumentType = data.type as ArgumentType;
    const isHex = argumentType.dataEncoding?.encoding === 'UNSIGNED';
    this.dataSource.filterPredicate = (enumValue, filter) => {
      const { label, value } = enumValue;
      return label.toLowerCase().indexOf(filter) >= 0
        || String(value).indexOf(filter) >= 0
        || (isHex && Number(value).toString(16).indexOf(filter) >= 0);
    };

    this.dataSource.data = argumentType.enumValue || [];
    if (isHex) {
      this.displayedColumns.push('hex');
    }
  }

  ngAfterViewInit() {
    this.filterControl.valueChanges.subscribe(() => {
      const value = this.filterControl.value || '';
      this.dataSource.filter = value.toLowerCase();
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

  toHex(value: string) {
    return Number(value).toString(16);
  }

  applySelection() {
    const selected = this.selection.selected;
    if (selected.length) {
      this.dialogRef.close(selected[0]);
    }
  }
}
