import { Component, computed, forwardRef, Input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatIcon } from '@angular/material/icon';
import { DataTableDirective } from '../../directives/data-table.directive';

interface ScriptItem {
  name: string;
  fullPath: string;
  type: 'folder' | 'file';
  parent: string;
}

@Component({
  selector: 'ya-file-picker',
  templateUrl: './file-picker.component.html',
  styleUrls: ['./file-picker.component.css'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => YaFilePicker),
      multi: true,
    },
  ],
  host: {
    class: 'ya-file-picker',
  },
  imports: [DataTableDirective, MatIcon],
})
export class YaFilePicker implements ControlValueAccessor {
  private allItems = signal<ScriptItem[]>([]);
  currentDir = signal<string>('');
  selectedPath = signal<string | null>(null);
  isDisabled = signal(false);

  onChange: any = () => {};
  onTouched: any = () => {};

  @Input({ required: true }) set scripts(paths: string[]) {
    this.allItems.set(this.parsePaths(paths));
    this.currentDir.set('');
    this.selectedPath.set(null);
  }

  // Filters items for current dir and ensures folders are always on top
  visibleItems = computed(() => {
    return this.allItems()
      .filter((item) => item.parent === this.currentDir())
      .sort((a, b) => {
        if (a.type !== b.type) return a.type === 'folder' ? -1 : 1;
        return a.name.localeCompare(b.name);
      });
  });

  writeValue(value: string): void {
    this.selectedPath.set(value);
    if (!value) {
      this.currentDir.set('');
    }
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.isDisabled.set(isDisabled);
  }

  private parsePaths(paths: string[]): ScriptItem[] {
    const itemMap = new Map<string, ScriptItem>();
    paths.forEach((path) => {
      const parts = path.split('/');
      let runningPath = '';
      parts.forEach((part, i) => {
        const parent = runningPath;
        runningPath += (runningPath ? '/' : '') + part;
        const isFile = i === parts.length - 1;
        if (!itemMap.has(runningPath)) {
          itemMap.set(runningPath, {
            name: part,
            fullPath: runningPath,
            type: isFile ? 'file' : 'folder',
            parent,
          });
        }
      });
    });
    return Array.from(itemMap.values());
  }

  handleFolderClick(item: ScriptItem) {
    if (this.isDisabled()) return;
    this.currentDir.set(item.fullPath);

    // Avoid invisible selection
    if (this.selectedPath()) {
      this.selectedPath.set(null);
      this.onChange(null);
      this.onTouched();
    }
  }

  handleItemClick(item: ScriptItem) {
    if (this.isDisabled()) return;
    if (item.type === 'folder') {
      // Ignore
    } else {
      if (this.selectedPath() !== item.fullPath) {
        this.selectedPath.set(item.fullPath);
        this.onChange(item.fullPath);
        this.onTouched();
      } else {
        this.selectedPath.set(null);
        this.onChange(null);
        this.onTouched();
      }
    }
  }

  goUp() {
    const parts = this.currentDir().split('/');
    parts.pop();
    this.currentDir.set(parts.join('/'));

    // Avoid invisible selection
    if (this.selectedPath()) {
      this.selectedPath.set(null);
      this.onChange(null);
      this.onTouched();
    }
  }
}
