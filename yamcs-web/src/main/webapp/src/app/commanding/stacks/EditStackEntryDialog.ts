import { ChangeDetectorRef, Component, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { utils } from '../../../lib';
import { Command, CommandOptionType, Value } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandSelector } from '../../shared/forms/CommandSelector';
import { CommandForm, TemplateProvider } from '../command-sender/CommandForm';
import { AdvanceOnParams, StackEntry } from './StackEntry';

export interface CommandResult {
  command: Command;
  args: { [key: string]: any; };
  extra: { [key: string]: Value; };
  comment?: string;
  stackOptions?: any;
}


export class StackEntryTemplateProvider implements TemplateProvider {

  constructor(private entry: StackEntry) {
  }

  getAssignment(argumentName: string) {
    for (const argName in this.entry.args) {
      if (argName === argumentName) {
        const value = this.entry.args[argName];
        return utils.toValue(value);
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

  stackOptionsForm: UntypedFormGroup;
  predefinedAcks = {
    Acknowledge_Queued: "Queued",
    Acknowledge_Released: "Released",
    Acknowledge_Sent: "Sent",
    Verifier_Queued: "Verifier_Queued",
    Verifier_Started: "Verifier_Started",
    Verifier_Complete: "Verifier_Complete",
  };
  predefinedAcksArray = Object.entries(this.predefinedAcks).map(ack => { return { name: ack[0], verboseName: ack[1] }; });

  // Captured in separate subject to avoid referencing
  // the form nested in *ngIf from outside the *ngIf.
  commandFormValid$ = new BehaviorSubject<boolean>(false);

  selectCommandForm: UntypedFormGroup;

  selectedCommand$ = new BehaviorSubject<Command | null>(null);
  templateProvider: StackEntryTemplateProvider | null;

  format: "json" | "xml";

  constructor(
    private dialogRef: MatDialogRef<EditStackEntryDialog>,
    readonly yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
    private changeDetection: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    let advanceOnAckDropDownDefault;
    let advanceOnAckCustomDefault;
    let advanceOnDelayDefault;
    if (data?.entry) {
      this.templateProvider = new StackEntryTemplateProvider(data.entry);
      this.selectedCommand$.next(data.entry.command);
      advanceOnAckDropDownDefault = data?.entry.advanceOn?.ack &&
        (Object.keys(this.predefinedAcks).includes(data?.entry.advanceOn?.ack) || data?.entry.advanceOn?.ack === "NONE" ? data?.entry.advanceOn?.ack : "custom");
      advanceOnAckCustomDefault = !Object.keys(this.predefinedAcks).includes(data?.entry.advanceOn?.ack) && data?.entry.advanceOn?.ack !== "NONE" ? data?.entry.advanceOn?.ack : '';
      advanceOnDelayDefault = data?.entry.advanceOn?.delay;
    }
    if (data?.okLabel) {
      this.okLabel = data?.okLabel;
    }
    if (data?.format) {
      this.format = data.format;
    }

    this.stackOptionsForm = formBuilder.group({
      advanceOnAckDropDown: [advanceOnAckDropDownDefault, []],
      advanceOnAckCustom: [advanceOnAckCustomDefault, []],
      advanceOnDelay: [advanceOnDelayDefault, []],
    });

    if (this.format !== "json") {
      this.stackOptionsForm.disable();
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
    const stackOptions: { advanceOn?: AdvanceOnParams; } = {};
    const advanceOnAckDropDown = this.stackOptionsForm.get("advanceOnAckDropDown")?.value;
    const advanceOnAckCustom = this.stackOptionsForm.get("advanceOnAckCustom")?.value?.trim();
    const advanceOnDelay = this.stackOptionsForm.get("advanceOnDelay")?.value;

    if ((advanceOnAckDropDown && advanceOnAckDropDown !== "custom") ||
      (advanceOnAckDropDown === "custom" && advanceOnAckCustom) || advanceOnDelay != null) {
      stackOptions.advanceOn = {
        ...(advanceOnAckDropDown && advanceOnAckDropDown !== "custom" && { ack: advanceOnAckDropDown }),
        ...(advanceOnAckDropDown === "custom" && advanceOnAckCustom && { ack: advanceOnAckCustom }),
        ...(advanceOnDelay != null && { delay: advanceOnDelay })
      };
    }

    const result: CommandResult = {
      command: this.selectedCommand$.value!,
      args: this.commandForm.getAssignments(),
      comment: this.commandForm.getComment(),
      extra: this.commandForm.getExtraOptions(),
      stackOptions: stackOptions
    };
    this.dialogRef.close(result);
  }

  returnToList(system: string) {
    this.templateProvider = null;
    this.selectedCommand$.next(null);
    this.changeDetection.detectChanges(); // Ensure ngIf resolves and #commandSelector is set
    this.selectCommandForm.reset();
    this.stackOptionsForm.reset();
    this.commandSelector.changeSystem(system === '/' ? '' : system);
  }
}
