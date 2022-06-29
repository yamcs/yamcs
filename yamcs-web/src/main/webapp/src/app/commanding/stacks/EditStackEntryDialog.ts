import { ChangeDetectorRef, Component, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { Command, CommandOptionType, Value } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandSelector } from '../../shared/forms/CommandSelector';
import { CommandForm, TemplateProvider } from '../command-sender/CommandForm';
import { StackEntry } from './StackEntry';

export interface CommandResult {
  command: Command;
  args: { [key: string]: any; };
  extra: { [key: string]: Value; };
  comment?: string;
}


export class StackEntryTemplateProvider implements TemplateProvider {

  constructor(private entry: StackEntry) {
  }

  getAssignment(argumentName: string) {
    for (const argName in this.entry.args) {
      if (argName === argumentName) {
        const value = this.entry.args[argName];
        return { type: 'STRING', stringValue: value } as Value;
      }
    }
  }

  getOption(id: string, expectedType: CommandOptionType) {
    for (const extraId in (this.entry.extra || {})) {
      if (extraId === id) {
        const value = this.entry.extra![extraId];
        switch (expectedType) {
          case 'BOOLEAN':
            return this.getBooleanOption(value);
          case 'NUMBER':
            return this.getNumberOption(value);
          case 'STRING':
            return this.getStringOption(value);
        }
      }
    }
  }

  private getBooleanOption(value: Value) {
    if (value.type === 'BOOLEAN') {
      return value;
    }
  }

  private getNumberOption(value: Value) {
    switch (value.type) {
      case 'SINT32':
      case 'UINT32':
      case 'SINT64':
      case 'UINT64':
        return value;
    }
  }

  private getStringOption(value: Value) {
    if (value.type === 'STRING') {
      return value;
    }
  }

  getComment() {
    return this.entry.comment;
  }
}


@Component({
  templateUrl: './EditStackEntryDialog.html',
  styleUrls: ['./EditStackEntryDialog.css'],
})
export class EditStackEntryDialog {

  okLabel = 'OK';

  @ViewChild('commandSelector')
  commandSelector: CommandSelector;

  @ViewChild('commandForm')
  commandForm: CommandForm;

  // Captured in separate subject to avoid referencing
  // the form nested in *ngIf from outside the *ngIf.
  commandFormValid$ = new BehaviorSubject<boolean>(false);

  selectCommandForm: UntypedFormGroup;

  selectedCommand$ = new BehaviorSubject<Command | null>(null);
  templateProvider: StackEntryTemplateProvider | null;

  constructor(
    private dialogRef: MatDialogRef<EditStackEntryDialog>,
    readonly yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
    private changeDetection: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    if (data?.entry) {
      this.templateProvider = new StackEntryTemplateProvider(data.entry);
      this.selectedCommand$.next(data.entry.command);
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
    const result: CommandResult = {
      command: this.selectedCommand$.value!,
      args: this.commandForm.getAssignments(),
      comment: this.commandForm.getComment(),
      extra: this.commandForm.getExtraOptions(),
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
