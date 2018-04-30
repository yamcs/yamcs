import {
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';
import { MatDialog } from '@angular/material';
import { StartReplayDialog } from './StartReplayDialog';

@Component({
  selector: 'app-monitor-toolbar',
  templateUrl: './MonitorToolbar.html',
  styleUrls: ['./MonitorToolbar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorToolbar {

  constructor(private dialog: MatDialog) {
  }

  startReplay() {
    this.dialog.open(StartReplayDialog, {
      width: '400px',
    });
  }
}
