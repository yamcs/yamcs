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
  prefixChange = new EventEmitter<string>();

  @Output()
  select = new EventEmitter<ObjectInfo>();

  @Output()
  close = new EventEmitter<void>();

  ngOnChanges() {
    if (!this.folder) {
      return;
    }
  }

  selectParent() {
    const prefix = this.folder.location;
    const idx = prefix.substring(0, prefix.length - 1).lastIndexOf('/');
    const parentPrefix = prefix.substring(0, idx + 1);
    this.prefixChange.emit(parentPrefix);
  }

  selectPrefix(prefix: string) {
    this.prefixChange.emit(prefix);
  }

  selectObject(object: ObjectInfo) {
    this.select.next(object);
  }
}
