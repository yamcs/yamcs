import { Component, input, Input, OnInit } from '@angular/core';
import { MatDivider } from '@angular/material/divider';
import { MatIcon } from '@angular/material/icon';
import {
  MatMenu,
  MatMenuContent,
  MatMenuItem,
  MatMenuTrigger,
} from '@angular/material/menu';
import { BehaviorSubject } from 'rxjs';
import { Preferences } from '../../services/preferences.service';
import { YaButton } from '../button/button.component';

export interface YaColumnInfo {
  id: string;
  label: string;
  visible?: boolean;
  alwaysVisible?: boolean;
  width?: string;

  /**
   * If set, the column is listed together with other columns of the
   * same group, separated by dividers from other menu entries.
   */
  group?: string;
}

interface MenuEntry {
  column: YaColumnInfo;
  divider: boolean;
}

interface StoredColumnInfo {
  id: string;
  visible: boolean;
}

@Component({
  selector: 'ya-column-chooser',
  templateUrl: './column-chooser.component.html',
  imports: [
    MatDivider,
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

  menuEntries: MenuEntry[] = [];

  constructor(private prefs: Preferences) {}

  ngOnInit() {
    this.recalculate(this.columns);
  }

  recalculate(columns: YaColumnInfo[]) {
    this.columns = columns;

    // Gather grouped columns at the position of the first column of their group
    const ordered: YaColumnInfo[] = [];
    const groupColumns = new Map<string, YaColumnInfo[]>();
    for (const column of columns) {
      if (column.alwaysVisible) {
        continue;
      }
      if (column.group) {
        let group = groupColumns.get(column.group);
        if (!group) {
          group = [];
          groupColumns.set(column.group, group);
          ordered.push(column);
        }
        group.push(column);
      } else {
        ordered.push(column);
      }
    }
    this.menuEntries = [];
    for (const column of ordered) {
      const members = column.group ? groupColumns.get(column.group)! : [column];
      for (const member of members) {
        const prev = this.menuEntries[this.menuEntries.length - 1];
        this.menuEntries.push({
          column: member,
          divider: !!prev && prev.column.group !== member.group,
        });
      }
    }

    const preferenceKey = this.preferenceKey();
    const storedColumnsById = new Map<string, StoredColumnInfo>();
    if (preferenceKey) {
      let storedColumns = this.getStoredColumns(preferenceKey + '.cols');

      // Filter out unknown columns
      storedColumns = storedColumns.filter((el) => {
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

  private getStoredColumns(key: string): StoredColumnInfo[] {
    const storedColumns = this.prefs.getObject<StoredColumnInfo[]>(key, []);
    for (let i = 0; i < storedColumns.length; i++) {
      const col = storedColumns[i];

      // In previous versions the type was a string[] of visible
      // columns. This has since been revised
      if (typeof col === 'string') {
        storedColumns[i] = { id: col, visible: true };
      }
    }

    return storedColumns;
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
    const preferenceKey = this.preferenceKey() + '.cols';
    if (preferenceKey) {
      this.prefs.setObject(preferenceKey, value);
    }
    const visibleIds = value.filter((v) => v.visible).map((v) => v.id);
    this.displayedColumns$.next(visibleIds);
  }
}
