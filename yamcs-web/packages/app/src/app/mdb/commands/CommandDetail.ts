import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatDialog } from '@angular/material';
import { Argument, Command, Instance } from '@yamcs/client';
import { ArgumentEnumDialog } from './ArgumentEnumDialog';

@Component({
  selector: 'app-command-detail',
  templateUrl: './CommandDetail.html',
  styleUrls: ['./CommandDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandDetail {

  @Input()
  instance: Instance;

  @Input()
  command: Command;

  constructor(private dialog: MatDialog) {
  }

  showEnum(argument: Argument) {
    this.dialog.open(ArgumentEnumDialog, {
      width: '400px',
      data: { argument },
    });
  }
}
