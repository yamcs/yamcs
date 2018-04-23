import { Component, ChangeDetectionStrategy, EventEmitter, Input, Output } from '@angular/core';

export interface ColumnInfo {
  id: string;
  label: string;
  alwaysVisible?: boolean;
  width?: string;
}

@Component({
  selector: 'app-column-chooser',
  templateUrl: './ColumnChooser.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ColumnChooser {

  @Input()
  columns: ColumnInfo[];

  @Input()
  displayedColumns: string[];

  @Output()
  change = new EventEmitter<string[]>();

  isVisible(column: ColumnInfo) {
    return this.displayedColumns.indexOf(column.id) >= 0;
  }

  toggleColumn(column: ColumnInfo) {
    const newDisplayedColumns = [];
    for (const c of this.columns) {
      if (column.id === c.id && !this.isVisible(c)) {
        newDisplayedColumns.push(c.id);
      } else if (column.id !== c.id && this.isVisible(c)) {
        newDisplayedColumns.push(c.id);
      }
    }
    this.displayedColumns = newDisplayedColumns;
    this.change.emit(newDisplayedColumns);
  }
}
