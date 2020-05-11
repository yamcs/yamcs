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
  path: string;

  @Output()
  prefixChange = new EventEmitter<string | null>();

  displayedColumns = ['name', 'size', 'modified'];
  dataSource = new MatTableDataSource<BrowseItem>([]);

  currentPrefix$ = new BehaviorSubject<string | null>(null);
  selectedObject$ = new BehaviorSubject<BrowseItem | null>(null);

  private storageClient: StorageClient;

  private onChange = (_: string | null) => { };
  private onTouched = () => { };

  private selectionSubscription: Subscription;

  constructor(yamcs: YamcsService, private changeDetection: ChangeDetectorRef) {
    this.storageClient = yamcs.createStorageClient();
    this.selectionSubscription = this.selectedObject$.subscribe(item => {
      if (item) {
        return this.onChange(item.name);
      } else {
        return this.onChange(null);
      }
    });
  }

  ngOnChanges() {
    if (this.instance && this.bucket) {
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

    this.storageClient.listObjects(this.instance, this.bucket.name, options).then(dir => {
      this.changedir(dir);
      const newPrefix = prefix || null;
      if (newPrefix !== this.currentPrefix$.value) {
        this.currentPrefix$.next(newPrefix);
        this.prefixChange.emit(newPrefix);
      }
    });
  }

  private changedir(dir: ListObjectsResponse) {
    this.selectedObject$.next(null);
    const items: BrowseItem[] = [];
    for (const prefix of dir.prefixes || []) {
      items.push({
        folder: true,
        name: prefix,
      });
    }
    for (const object of dir.objects || []) {
      items.push({
        folder: false,
        name: object.name,
        modified: object.created,
        size: object.size,
        objectUrl: this.storageClient.getObjectURL(this.instance, this.bucket.name, object.name),
      });
    }
    this.dataSource.data = items;
    this.changeDetection.detectChanges();
  }

  selectFile(row: BrowseItem) {
    if (row.folder) {
      this.selectedObject$.next(null);
      this.loadCurrentFolder(row.name);
    } else {
      this.selectedObject$.next(row);
    }
  }

  selectParent() {
    const currentPrefix = this.currentPrefix$.value;
    if (currentPrefix) {
      const withoutTrailingSlash = currentPrefix.slice(0, -1);
      const idx = withoutTrailingSlash.lastIndexOf('/');
      if (idx) {
        const parentPrefix = withoutTrailingSlash.substring(0, idx + 1);
        this.selectedObject$.next(null);
        this.loadCurrentFolder(parentPrefix);
      }
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
