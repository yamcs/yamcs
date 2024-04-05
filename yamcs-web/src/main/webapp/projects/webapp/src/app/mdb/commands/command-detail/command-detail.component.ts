import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Argument, Command, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { SignificanceLevelComponent } from '../../../shared/significance-level/significance-level.component';
import { ArgumentEnumDialogComponent } from '../argument-enum-dialog/argument-enum-dialog.component';

@Component({
  standalone: true,
  selector: 'app-command-detail',
  templateUrl: './command-detail.component.html',
  styleUrl: './command-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MarkdownComponent,
    WebappSdkModule,
    SignificanceLevelComponent,
  ],
})
export class CommandDetailComponent {

  @Input()
  command: Command;

  constructor(readonly yamcs: YamcsService, private dialog: MatDialog) {
  }

  showEnum(argument: Argument) {
    this.dialog.open(ArgumentEnumDialogComponent, {
      width: '400px',
      data: { argument },
    });
  }
}
