import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { PreferenceStore } from '../../services/preference-store.service';

export interface ColumnInfo {
  id: string;
  label: string;
  visible?: boolean;
  alwaysVisible?: boolean;
  width?: string;
}

@Component({
  selector: 'ya-column-chooser',
  templateUrl: './column-chooser.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ColumnChooserComponent implements OnInit {

  @Input()
  columns: ColumnInfo[];

  @Input()
  preferenceKey: string;

  displayedColumns$ = new BehaviorSubject<string[]>([]);

  constructor(private preferenceStore: PreferenceStore) {
  }

  ngOnInit() {
    this.recalculate(this.columns);
  }

  recalculate(columns: ColumnInfo[]) {
    this.columns = columns;

    let preferredColumns: string[] = [];
    if (this.preferenceKey) {
      const storedDisplayedColumns = this.preferenceStore.getVisibleColumns(this.preferenceKey);
      preferredColumns = (storedDisplayedColumns || []).filter(el => {
        // Filter out unknown columns
        for (const column of this.columns) {
          if (column.id === el) {
            return true;
          }
        }
      });
    }

    // Keep a column if it's either from preferences, or is to be always visible.
    const displayedColumns: string[] = [];
    for (const column of this.columns) {
      if (column.visible || column.alwaysVisible || preferredColumns.indexOf(column.id) !== -1) {
        displayedColumns.push(column.id);
      }
    }

    this.displayedColumns$.next(displayedColumns);
  }

  isVisible(column: ColumnInfo) {
    const displayedColumns = this.displayedColumns$.value;
    return displayedColumns && displayedColumns.indexOf(column.id) >= 0;
  }

  writeValue(value: any) {
    if (this.preferenceKey) {
      this.preferenceStore.setVisibleColumns(this.preferenceKey, value);
    }
    this.displayedColumns$.next(value);
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
    this.writeValue(newDisplayedColumns);
  }
}
