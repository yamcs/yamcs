import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { ObjectInfo } from '@yamcs/client';
import { DisplayFolder } from './DisplayFolder';

@Component({
  selector: 'app-display-navigator',
  templateUrl: './DisplayNavigator.html',
  styleUrls: ['./DisplayNavigator.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayNavigator implements OnChanges {

  @Input()
  folder: DisplayFolder;

  @Output()
  pathChange = new EventEmitter<string>();

  @Output()
  select = new EventEmitter<ObjectInfo>();

  @Output()
  close = new EventEmitter<void>();

  ngOnChanges() {
    if (!this.folder) {
      return;
    }
  }

  selectFolder(prefix: string) {
    this.pathChange.emit(prefix);
  }

  selectParent() {
    const currentPath = this.folder.location;
    const nameLen = this.folder.name.length + 1;
    const parentPath = currentPath.substring(0, currentPath.length - nameLen);
    this.pathChange.emit(parentPath);
  }

  selectFile(file: ObjectInfo) {
    this.select.next(file);
  }
}
