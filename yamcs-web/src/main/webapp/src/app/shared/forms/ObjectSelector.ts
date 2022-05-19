import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, forwardRef, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Bucket, ListObjectsOptions, ListObjectsResponse, StorageClient } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-object-selector',
  templateUrl: './ObjectSelector.html',
  styleUrls: ['./ObjectSelector.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ObjectSelector),
      multi: true
    }
  ]
})
export class ObjectSelector implements ControlValueAccessor, OnChanges, OnDestroy {

  @Input()
  instance = '_global';

  @Input()
  bucket: Bucket;

  @Input()
  isMultiSelect: boolean;

  @Input()
  foldersOnly: boolean;

  @Input()
  path: string;

  @Output()
  prefixChange = new EventEmitter<string | null>();

  displayedColumns = ['name', 'size', 'modified'];
  dataSource = new MatTableDataSource<BrowseItem>([]);

  currentPrefix$ = new BehaviorSubject<string | null>(null);

  private storageClient: StorageClient;
  private selectedFileNames: Set<string> = new Set();

  private onChange = (_: string | null) => { };
  private onTouched = () => { };

  private selectionSubscription: Subscription;

  constructor(yamcs: YamcsService, private changeDetection: ChangeDetectorRef) {
    this.storageClient = yamcs.createStorageClient();
  }

  // Called when bucket is changed
  ngOnChanges() {
    if (this.instance) {
      this.loadCurrentFolder();
    }
  }

  // Called when breadcrumb is selected
  changePrefix(prefix: string) {
    this.loadCurrentFolder(prefix);
  }

  private loadCurrentFolder(prefix?: string) {
    if (!this.bucket) {
      // The object selector for remote files does not have a bucket.
      // Show an empty folder, which will trigger prefixChange() to update breadcrumb and get the file list.
      const newPrefix = prefix || null;
      // Only clear folder (and get new file list) if we are entering a different folder.
      // If the user selects the same folder at the breadcrumb we must not clear the table.
      if (newPrefix !== this.currentPrefix$.value) {
        const dir: ListObjectsResponse = { prefixes: [], objects: [] };
        this.setFolderContent(prefix, dir);
      }
      return;
    }

    const options: ListObjectsOptions = {
      delimiter: '/',
    };
    if (prefix) {
      options.prefix = prefix;
    }

    this.storageClient.listObjects(this.instance, this.bucket.name, options).then(dir => {
      this.setFolderContent(prefix, dir);
    });
  }

  // Update html table and breadcrumb
  setFolderContent(prefix: string | undefined, dir: ListObjectsResponse) {
    this.changeDir(dir); // Update html table
    const newPrefix = prefix || null;
    if (newPrefix !== this.currentPrefix$.value) {
      this.currentPrefix$.next(newPrefix); // Show or hide parent folder
      this.prefixChange.emit(newPrefix); // Update breadcrumb
    }
  }

  // Update form; update dataSource and notify html to update table
  private changeDir(dir: ListObjectsResponse) {
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
      const url: string = this.bucket != null ? this.storageClient.getObjectURL(this.instance, this.bucket.name, object.name) : '';
      items.push({
        folder: false,
        name: object.name,
        modified: object.created,
        size: object.size,
        objectUrl: url,
      });
    }
    this.dataSource.data = items;
    this.changeDetection.detectChanges();
  }

  // Called from html when a row is selected
  selectFile(row: BrowseItem) {
    if (row.folder) {
      this.loadCurrentFolder(row.name);
    } else if (!this.foldersOnly) {
      if (this.isMultiSelect) {
        if (this.selectedFileNames.has(row.name)) {
          this.selectedFileNames.delete(row.name);
        } else {
          this.selectedFileNames.add(row.name);
        }
      } else {
        this.selectedFileNames.clear();
        this.selectedFileNames.add(row.name);
      }
      this.updateFileNames();
    }
  }

  // Update form
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
      const parentPrefix = idx != -1 ? withoutTrailingSlash.substring(0, idx + 1) : undefined;
      this.selectedFileNames.clear();
      this.updateFileNames();
      this.loadCurrentFolder(parentPrefix);
    }
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
