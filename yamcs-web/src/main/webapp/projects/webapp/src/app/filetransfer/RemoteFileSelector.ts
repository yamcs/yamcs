import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, forwardRef, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { ListFilesResponse } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  selector: 'remote-file-selector',
  templateUrl: './RemoteFileSelector.html',
  styleUrls: ['./RemoteFileSelector.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => RemoteFileSelector),
      multi: true
    }
  ]
})
export class RemoteFileSelector implements ControlValueAccessor, OnChanges, OnDestroy {

  @Input()
  isMultiSelect: boolean;

  @Input()
  foldersOnly: boolean;

  @Input()
  path: string;

  @Input()
  noSelect = false;

  @Input()
  allowFolderSelection = false;

  @Output()
  prefixChange = new EventEmitter<string | null>();

  displayedColumns = ['name', 'size', 'modified'];
  dataSource = new MatTableDataSource<RemoteFileItem>([]);

  currentPrefix$ = new BehaviorSubject<string | null>(null);

  private selectedFileNames: Set<string> = new Set();
  private lastSelected: RemoteFileItem;

  private onChange = (_: string | null) => { };
  private onTouched = () => { };

  private selectionSubscription: Subscription;

  constructor(private changeDetection: ChangeDetectorRef) {
  }

  // Called when inputs have changed
  ngOnChanges() {
    this.loadCurrentFolder();
  }

  // Called when breadcrumb is selected
  changePrefix(prefix: string) {
    this.loadCurrentFolder(prefix);
  }

  private loadCurrentFolder(prefix?: string) {
    // Show an empty folder, which will trigger prefixChange() to update breadcrumb and get the file list.
    const newPrefix = prefix || null;

    // Only clear folder (and get new file list) if we are entering a different folder.
    // If the user selects the same folder at the breadcrumb we must not clear the table.
    if (newPrefix !== this.currentPrefix$.value) {
      const dir: ListFilesResponse = { files: [], destination: '', remotePath: '', listTime: '' };
      this.setFolderContent(prefix, dir);
    }
  }

  // Update html table and breadcrumb
  setFolderContent(prefix: string | undefined, dir: ListFilesResponse) {
    this.changeDir(dir); // Update html table
    const newPrefix = prefix || null;
    if (newPrefix !== this.currentPrefix$.value) {
      this.currentPrefix$.next(newPrefix); // Show or hide parent folder
      this.prefixChange.emit(newPrefix); // Update breadcrumb
    }
  }

  // Update form; update dataSource and notify html to update table
  private changeDir(dir: ListFilesResponse) {
    this.selectedFileNames.clear();
    this.updateFileNames();
    const items: RemoteFileItem[] = [];
    const prefix: string = dir.remotePath ? dir.remotePath + '/' : '';
    for (const file of dir.files || []) {
      const fullFileName = prefix + file.name;
      items.push({
        folder: file.isDirectory,
        name: fullFileName,
        modified: file.modified,
        size: file.size,
      });
    }
    this.dataSource.data = items;
    this.changeDetection.detectChanges();
  }

  // Called from html when a row is selected
  selectFile(event: MouseEvent, row: RemoteFileItem) {
    if (row.folder) {
      if (this.allowFolderSelection && event.ctrlKey && event.shiftKey) {
        this.flipRowSelection(row);
      } else {
        this.loadCurrentFolder(row.name);
      }
    } else if (!this.foldersOnly) {
      if (this.isMultiSelect && event.ctrlKey) {
        this.flipRowSelection(row);
      } else if (this.isMultiSelect && event.shiftKey) {
        if (this.selectedFileNames.size == 0 || !this.lastSelected || this.lastSelected.name === row.name) {
          this.flipRowSelection(row);
        } else {
          let select = false;
          for (const candidate of this.dataSource.data) {
            if (candidate.name === row.name || candidate.name === this.lastSelected.name) {
              select = !select;
            }
            if (select) {
              this.selectedFileNames.add(candidate.name);
            }
          }
          this.selectedFileNames.add(row.name);
        }
      } else {
        if (this.selectedFileNames.size == 1 && this.selectedFileNames.has(row.name)) {
          this.selectedFileNames.clear();
        } else {
          this.selectedFileNames.clear();
          this.selectedFileNames.add(row.name);
        }
      }

      if (this.selectedFileNames.has(row.name)) {
        this.lastSelected = row;
      }
    }

    this.updateFileNames();
  }

  private flipRowSelection(row: RemoteFileItem) {
    if (this.selectedFileNames.has(row.name)) {
      this.selectedFileNames.delete(row.name);
    } else {
      this.selectedFileNames.add(row.name);
    }
  }

  // Update form
  private updateFileNames() {
    this.onChange(Array.from(this.selectedFileNames).join("|"));
  }

  isSelected(row: RemoteFileItem) {
    return this.selectedFileNames.has(row.name);
  }

  selectParent() {
    const currentPrefix = this.currentPrefix$.value;
    if (currentPrefix) {
      const withoutTrailingSlash = currentPrefix.slice(0, -1);
      const idx = withoutTrailingSlash.lastIndexOf('/');
      const parentPrefix = idx != -1 ? withoutTrailingSlash.substring(0, idx + 1) : undefined;
      this.selectedFileNames.clear();
      this.updateFileNames();
      this.loadCurrentFolder(parentPrefix);
    }
  }

  clearSelection() {
    this.selectedFileNames.clear();
    this.updateFileNames();
    this.changeDetection.detectChanges();
  }

  writeValue(value: any) {
    this.path = value;
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
    this.onTouched = fn;
  }

  ngOnDestroy() {
    this.selectionSubscription?.unsubscribe();
  }
}

export class RemoteFileItem {
  folder: boolean;
  name: string;
  modified?: string;
  size?: number;
}
