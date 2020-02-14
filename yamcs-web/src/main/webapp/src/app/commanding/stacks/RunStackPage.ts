import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { ConnectionInfo, Instance } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { AddCommandDialog, CommandResult } from './AddCommandDialog';
import { StackEntry } from './StackEntry';

@Component({
  templateUrl: './RunStackPage.html',
  styleUrls: ['./RunStackPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunStackPage {

  connectionInfo$: Observable<ConnectionInfo | null>;
  instance: Instance;

  displayedColumns = [
    'seq',
    'command',
    'comment',
    'actions',
  ];

  dataSource = new MatTableDataSource<StackEntry>();

  constructor(
    title: Title,
    private yamcs: YamcsService,
    private dialog: MatDialog,
  ) {
    title.setTitle('Run a stack');
    this.connectionInfo$ = yamcs.connectionInfo$!;
    this.instance = yamcs.getInstance();
  }

  addEntry() {
    const dialogRef = this.dialog.open(AddCommandDialog, {
      width: '70%',
      height: '100%',
      autoFocus: false,
      position: {
        right: '0',
      }
    });

    dialogRef.afterClosed().subscribe((result?: CommandResult) => {
      if (result) {
        const entry: StackEntry = {
          name: result.command.qualifiedName,
          arguments: result.assignments,
          command: result.command,
        };

        this.dataSource.data = [
          ... this.dataSource.data,
          entry,
        ];
      }
    });
  }
}
