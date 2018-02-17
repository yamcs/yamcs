import { Component, ChangeDetectionStrategy, Input, Output, EventEmitter } from '@angular/core';
import { DisplayInfo, DisplayFile } from '../../../yamcs-client';

@Component({
  selector: 'app-display-navigator',
  templateUrl: './DisplayNavigator.html',
  styleUrls: ['./DisplayNavigator.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayNavigator {

  @Input()
  displayInfo: DisplayInfo;

  @Output()
  select = new EventEmitter<DisplayFile>();

  selectFile(file: DisplayFile) {
    this.select.next(file);
  }
}
