
import { ChangeDetectionStrategy, Component, input, Input, OnInit } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { BehaviorSubject } from 'rxjs';
import { PreferenceStore } from '../../services/preference-store.service';
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
    let preferredColumns: string[] = [];
    if (preferenceKey) {
      const storedDisplayedColumns = this.preferenceStore.getVisibleColumns(preferenceKey);
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

  isVisible(column: YaColumnInfo) {
    const displayedColumns = this.displayedColumns$.value;
    return displayedColumns && displayedColumns.indexOf(column.id) >= 0;
  }

  writeValue(value: any) {
    const preferenceKey = this.preferenceKey();
    if (preferenceKey) {
      this.preferenceStore.setVisibleColumns(preferenceKey, value);
    }
    this.displayedColumns$.next(value);
  }

  toggleColumn(column: YaColumnInfo) {
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
