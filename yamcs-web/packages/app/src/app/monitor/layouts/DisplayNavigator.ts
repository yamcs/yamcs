import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { DisplayFile, DisplayFolder } from '@yamcs/client';

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
  select = new EventEmitter<DisplayFile>();

  @Output()
  close = new EventEmitter<void>();

  ngOnChanges() {
    if (!this.folder) {
      return;
    }
  }

  selectFolder(folder: DisplayFolder) {
    this.pathChange.emit(folder.path);
  }

  selectParent() {
    const currentPath = this.folder.path;
    const nameLen = this.folder.name.length + 1;
    const parentPath = currentPath.substring(0, currentPath.length - nameLen);
    this.pathChange.emit(parentPath);
  }

  selectFile(file: DisplayFile) {
    this.select.next(file);
  }
}
