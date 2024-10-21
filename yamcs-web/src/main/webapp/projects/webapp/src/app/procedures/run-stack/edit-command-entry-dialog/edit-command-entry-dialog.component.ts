import { ChangeDetectorRef, Component, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AdvancementParams, Command, Value, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { CommandFormComponent } from '../../../commanding/command-sender/command-form/command-form.component';
import { CommandSelectorComponent } from '../../../shared/command-selector/command-selector.component';
import { AdvanceAckHelpComponent } from '../advance-ack-help/advance-ack-help.component';
import { StackedCommandEntry } from '../stack-file/StackedEntry';
import { CommandStepTemplateProvider } from './CommandStepTemplateProvider';

export interface CommandResult {
  command: Command;
  args: { [key: string]: any; };
  extra: { [key: string]: Value; };
  comment?: string;
  stream?: string;
  advancement?: AdvancementParams;
}

@Component({
  standalone: true,
  templateUrl: './edit-command-entry-dialog.component.html',
  styleUrl: './edit-command-entry-dialog.component.css',
  imports: [
    AdvanceAckHelpComponent,
    CommandFormComponent,
    CommandSelectorComponent,
    WebappSdkModule,
  ],
})
export class EditCommandEntryDialogComponent {

  okLabel = 'OK';

  @ViewChild('commandSelector')
  commandSelector: CommandSelectorComponent;

  @ViewChild('commandForm')
  commandForm: CommandFormComponent;

  // Captured in separate subject to avoid referencing
  // the form nested in *ngIf from outside the *ngIf.
  commandFormValid$ = new BehaviorSubject<boolean>(false);

  selectCommandForm: UntypedFormGroup;

  selectedCommand$ = new BehaviorSubject<Command | null>(null);
  templateProvider: CommandStepTemplateProvider | null;

  constructor(
    private dialogRef: MatDialogRef<EditCommandEntryDialogComponent>,
    readonly yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
    private changeDetection: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    if (data?.entry) {
      const entry = data.entry as StackedCommandEntry;
      this.templateProvider = new CommandStepTemplateProvider(entry.model);
      this.selectedCommand$.next(entry.command ?? null);
    }
    if (data?.okLabel) {
      this.okLabel = data?.okLabel;
    }

    this.selectCommandForm = formBuilder.group({
      command: ['', Validators.required],
    });

    this.selectCommandForm.valueChanges.subscribe(() => {
      const command = this.selectCommandForm.value['command'];
      this.selectedCommand$.next(command || null);
    });
  }

  handleOK() {
    const commandConfig = this.commandForm.getResult();

    const result: CommandResult = {
      command: this.selectedCommand$.value!,
      args: commandConfig.args,
      extra: commandConfig.extra,
      comment: commandConfig.comment,
      stream: commandConfig.stream,
      advancement: commandConfig.advancement,
    };
    this.dialogRef.close(result);
  }

  returnToList(system: string) {
    this.templateProvider = null;
    this.selectedCommand$.next(null);
    this.changeDetection.detectChanges(); // Ensure ngIf resolves and #commandSelector is set
    this.selectCommandForm.reset();
    this.commandSelector.changeSystem(system === '/' ? '' : system);
  }
}
