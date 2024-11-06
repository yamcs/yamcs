
import { ChangeDetectionStrategy, Component, input, Input, OnInit } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { BehaviorSubject } from 'rxjs';
import { PreferenceStore, StoredColumnInfo } from '../../services/preference-store.service';
import { YaButton } from '../button/button.component';

export interface YaColumnInfo {
  id: string;
  label: string;
  visible?: boolean;
  alwaysVisible?: boolean;
  width?: string;
}

@Component({
  standalone: true,
  selector: 'ya-column-chooser',
  templateUrl: './column-chooser.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatMenu,
    MatMenuContent,
    MatMenuItem,
    MatIcon,
    MatMenuTrigger,
    YaButton,
  ],
})
export class YaColumnChooser implements OnInit {

  @Input()
  columns: YaColumnInfo[];

  preferenceKey = input<string>();
  icon = input<string>();
  appearance = input('basic');

  displayedColumns$ = new BehaviorSubject<string[]>([]);

  constructor(private preferenceStore: PreferenceStore) {
  }

  ngOnInit() {
    this.recalculate(this.columns);
  }

  recalculate(columns: YaColumnInfo[]) {
    this.columns = columns;

    const preferenceKey = this.preferenceKey();
    const storedColumnsById = new Map<string, StoredColumnInfo>();
    if (preferenceKey) {
      const storedColumnInfo = this.preferenceStore.getStoredColumnInfo(preferenceKey);
      const storedColumns = (storedColumnInfo || []).filter(el => {
        // Filter out unknown columns
        for (const column of this.columns) {
          if (column.id === el.id) {
            return true;
          }
        }
      });
      for (const storedColumn of storedColumns) {
        storedColumnsById.set(storedColumn.id, storedColumn);
      }
    }

    // Keep a column if it's either from preferences, or is to be always visible.
    const displayedColumns: string[] = [];
    for (const column of this.columns) {
      const storedColumn = storedColumnsById.get(column.id);
      if (storedColumn) {
        if (storedColumn.visible || column.alwaysVisible) {
          displayedColumns.push(column.id);
        }
      } else if (column.visible || column.alwaysVisible) {
        displayedColumns.push(column.id);
      }
    }

    this.displayedColumns$.next(displayedColumns);
  }

  isVisible(column: YaColumnInfo) {
    const displayedColumns = this.displayedColumns$.value;
    return displayedColumns && displayedColumns.indexOf(column.id) >= 0;
  }

  toggleColumn(column: YaColumnInfo) {
    const newStoredColumns: StoredColumnInfo[] = [];
    for (const c of this.columns) {
      if (column.id === c.id && !this.isVisible(c)) {
        newStoredColumns.push({ id: c.id, visible: true });
      } else if (column.id !== c.id && this.isVisible(c)) {
        newStoredColumns.push({ id: c.id, visible: true });
      } else {
        newStoredColumns.push({ id: c.id, visible: false });
      }
    }
    this.writeValue(newStoredColumns);
  }

  private writeValue(value: StoredColumnInfo[]) {
    const preferenceKey = this.preferenceKey();
    if (preferenceKey) {
      this.preferenceStore.setVisibleColumns(preferenceKey, value);
    }
    const visibleIds = value.filter(v => v.visible).map(v => v.id);
    this.displayedColumns$.next(visibleIds);
  }
}
