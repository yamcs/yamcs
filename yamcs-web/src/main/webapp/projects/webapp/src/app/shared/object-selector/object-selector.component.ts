import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, forwardRef, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Bucket, ListObjectsOptions, ListObjectsResponse, StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-object-selector',
  templateUrl: './object-selector.component.html',
  styleUrl: './object-selector.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => ObjectSelector),
    multi: true
  }],
  imports: [
    WebappSdkModule,
  ],
})
export class ObjectSelector implements ControlValueAccessor, OnChanges, OnDestroy {

  @Input()
  bucket: Bucket;

  @Input()
  isMultiSelect: boolean;

  @Input()
  foldersOnly: boolean;

  @Input()
  noFrame: boolean = false;

  @Input()
  path: string;

  @Input()
  noSelect = false;

  @Input()
  allowFolderSelection = false;

  @Output()
  prefixChange = new EventEmitter<string | null>();

  displayedColumns = ['name', 'size', 'modified'];
  dataSource = new MatTableDataSource<BrowseItem>([]);

  currentPrefix$ = new BehaviorSubject<string | null>(null);

  private storageClient: StorageClient;
  private selectedFileNames: Set<string> = new Set();
  private lastSelected: BrowseItem;

  private onChange = (_: string | null) => { };
  private onTouched = () => { };

  private selectionSubscription: Subscription;

  constructor(yamcs: YamcsService, private changeDetection: ChangeDetectorRef) {
    this.storageClient = yamcs.createStorageClient();
  }

  ngOnChanges() {
    if (this.bucket) {
      this.loadCurrentFolder();
    }
  }

  changePrefix(prefix: string) {
    this.loadCurrentFolder(prefix);
  }

  private loadCurrentFolder(prefix?: string) {
    const options: ListObjectsOptions = {
      delimiter: '/',
    };
    if (prefix) {
      options.prefix = prefix;
    }

    this.storageClient.listObjects(this.bucket.name, options).then(dir => {
      this.changedir(dir);
      const newPrefix = prefix || null;
      if (newPrefix !== this.currentPrefix$.value) {
        this.currentPrefix$.next(newPrefix);
        this.prefixChange.emit(newPrefix);
      }
    });
  }

  private changedir(dir: ListObjectsResponse) {
    this.selectedFileNames.clear();
    this.updateFileNames();
    const items: BrowseItem[] = [];
    for (const prefix of dir.prefixes || []) {
      items.push({
        folder: true,
        name: prefix,
      });
    }
    for (const object of dir.objects || []) {
      // Ignore fake objects that represent an empty directory
      if (object.name.endsWith('/')) {
        continue;
      }
      items.push({
        folder: false,
        name: object.name,
        modified: object.created,
        size: object.size,
        objectUrl: this.storageClient.getObjectURL(this.bucket.name, object.name),
      });
    }
    this.dataSource.data = items;
    this.changeDetection.detectChanges();
  }

  // Called from html when a row is selected
  selectFile(event: MouseEvent, row: BrowseItem) {
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

  private flipRowSelection(row: BrowseItem) {
    if (this.selectedFileNames.has(row.name)) {
      this.selectedFileNames.delete(row.name);
    } else {
      this.selectedFileNames.add(row.name);
    }
  }

  private updateFileNames() {
    this.onChange(Array.from(this.selectedFileNames).join("|"));
  }

  isSelected(row: BrowseItem) {
    return this.selectedFileNames.has(row.name);
  }

  selectParent() {
    const currentPrefix = this.currentPrefix$.value;
    if (currentPrefix) {
      const withoutTrailingSlash = currentPrefix.slice(0, -1);
      const idx = withoutTrailingSlash.lastIndexOf('/');
      if (idx) {
        const parentPrefix = withoutTrailingSlash.substring(0, idx + 1);
        this.selectedFileNames.clear();
        this.updateFileNames();
        this.loadCurrentFolder(parentPrefix);
      }
    }
  }

  writeValue(value: any) {
    this.path = value;
  }

  clearSelection() {
    this.selectedFileNames.clear();
    this.updateFileNames();
    this.changeDetection.detectChanges();
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
    this.onTouched = fn;
  }

  ngOnDestroy() {
    if (this.selectionSubscription) {
      this.selectionSubscription.unsubscribe();
    }
  }
}

export class BrowseItem {
  folder: boolean;
  name: string;
  modified?: string;
  objectUrl?: string;
  size?: number;
}
