import { SelectionModel } from '@angular/cdk/collections';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  Input,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Subscription } from 'rxjs';

@Component({
  selector: 'ya-table-checkbox',
  templateUrl: './table-checkbox.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaTableCheckbox implements OnInit, OnDestroy {
  changeDetection = inject(ChangeDetectorRef);

  @Input({ required: true })
  dataSource: MatTableDataSource<any>;

  @Input({ required: true })
  selection: SelectionModel<any>;

  @Input()
  item?: any;

  /**
   * Optionally, transform a datasource row to a selection item
   */
  @Input()
  transform?: (item?: any) => any;

  private selectionSubscription: Subscription;

  ngOnInit(): void {
    this.selectionSubscription = this.selection.changed.subscribe(() => {
      this.changeDetection.detectChanges();
    });
  }

  toggle() {
    if (this.item) {
      const selectionItem = this.applyTransform(this.item);
      this.selection.toggle(selectionItem);
    } else {
      this.masterToggle();
    }
  }

  isChecked() {
    if (this.item) {
      const selectionItem = this.applyTransform(this.item);
      return this.selection.isSelected(selectionItem);
    } else {
      return this.selection.hasValue() && this.isAllSelected();
    }
  }

  private applyTransform(item: any) {
    if (this.transform) {
      return this.transform(item);
    } else {
      return item;
    }
  }

  private isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.filteredData.length;
    return numSelected === numRows && numRows > 0;
  }

  private masterToggle() {
    this.isAllSelected()
      ? this.selection.clear()
      : this.dataSource.filteredData.forEach((row) => {
          const selectionItem = this.transform ? this.transform(row) : row;
          this.selection.select(selectionItem);
        });
  }

  ngOnDestroy(): void {
    this.selectionSubscription?.unsubscribe();
  }
}
