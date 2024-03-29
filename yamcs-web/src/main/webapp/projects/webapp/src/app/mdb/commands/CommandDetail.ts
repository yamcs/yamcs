import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Argument, Command, YamcsService } from '@yamcs/webapp-sdk';
import { ArgumentEnumDialog } from './ArgumentEnumDialog';

@Component({
  selector: 'app-command-detail',
  templateUrl: './CommandDetail.html',
  styleUrl: './CommandDetail.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandDetail {

  @Input()
  command: Command;

  constructor(readonly yamcs: YamcsService, private dialog: MatDialog) {
  }

  showEnum(argument: Argument) {
    this.dialog.open(ArgumentEnumDialog, {
      width: '400px',
      data: { argument },
    });
  }
}
