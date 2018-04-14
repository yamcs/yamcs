import { Component, ChangeDetectionStrategy, Input, Output, EventEmitter, OnChanges } from '@angular/core';
import { DisplayFile, DisplayFolder } from '@yamcs/client';

@Component({
  selector: 'app-display-navigator',
  templateUrl: './DisplayNavigator.html',
  styleUrls: ['./DisplayNavigator.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayNavigator implements OnChanges {

  @Input()
  displayInfo: DisplayFolder;

  @Output()
  select = new EventEmitter<DisplayFile>();

  @Output()
  close = new EventEmitter<void>();

  currentFolder: DisplayFolder;

  ngOnChanges() {
    if (!this.displayInfo) {
      return;
    }

    this.currentFolder = this.displayInfo;
  }

  selectFolder(folder: DisplayFolder) {
    this.currentFolder = folder;
  }

  selectParent() {
    const currentPath = this.currentFolder.path;
    const nameLen = this.currentFolder.name.length + 1;
    const parentPath = currentPath.substring(0, currentPath.length - nameLen);
    const match = this.findDisplayFolder(parentPath, this.displayInfo);
    if (match) {
      this.currentFolder = match;
    }
  }

  selectFile(file: DisplayFile) {
    this.select.next(file);
  }

  findDisplayFolder(path: string, start: DisplayFolder): DisplayFolder | undefined {
    if (path === '/') {
      return this.displayInfo;
    }
    for (const folder of start.folder || []) {
      if (folder.path === path) {
        return folder;
      } else {
        const childFolder = this.findDisplayFolder(path, folder);
        if (childFolder) {
          return childFolder;
        }
      }
    }
  }
}
