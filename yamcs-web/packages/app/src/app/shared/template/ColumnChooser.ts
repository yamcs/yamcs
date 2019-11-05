import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { PreferenceStore } from '../../core/services/PreferenceStore';

export interface ColumnInfo {
  id: string;
  label: string;
  visible?: boolean;
  alwaysVisible?: boolean;
  width?: string;
}

@Component({
  selector: 'app-column-chooser',
  templateUrl: './ColumnChooser.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ColumnChooser implements OnInit {

  @Input()
  columns: ColumnInfo[];

  @Input()
  preferenceKey: string;

  displayedColumns$ = new BehaviorSubject<string[]>([]);

  constructor(private preferenceStore: PreferenceStore) {
  }

  ngOnInit() {
    let initialDisplayedColumns: string[] = [];
    for (const column of this.columns) {
      if (column.visible || column.alwaysVisible) {
        initialDisplayedColumns.push(column.id);
      }
    }


    if (this.preferenceKey) {
      const storedDisplayedColumns = this.preferenceStore.getVisibleColumns(this.preferenceKey);
      const filteredCols = (storedDisplayedColumns || []).filter(el => {
        // Filter out unknown columns
        for (const column of this.columns) {
          if (column.id === el) {
            return true;
          }
        }
      });
      if (filteredCols.length) {
        initialDisplayedColumns = filteredCols;
      }
    }

    this.displayedColumns$.next(initialDisplayedColumns);
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
