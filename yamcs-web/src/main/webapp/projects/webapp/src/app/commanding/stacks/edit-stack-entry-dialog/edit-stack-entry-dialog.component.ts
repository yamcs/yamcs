import { ChangeDetectorRef, Component, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AcknowledgmentInfo, AdvancementParams, Command, CommandOptionType, StackEntry, Value, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { CommandSelectorComponent } from '../../../shared/command-selector/command-selector.component';
import { CommandFormComponent, TemplateProvider } from '../../command-sender/command-form/command-form.component';
import { AdvanceAckHelpComponent } from '../advance-ack-help/advance-ack-help.component';

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

import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  templateUrl: './edit-stack-entry-dialog.component.html',
  styleUrl: './edit-stack-entry-dialog.component.css',
  imports: [
    AdvanceAckHelpComponent,
    CommandFormComponent,
    CommandSelectorComponent,
    WebappSdkModule,
  ],
})
export class EditStackEntryDialogComponent {

  okLabel = 'OK';

  @ViewChild('commandSelector')
  commandSelector: CommandSelectorComponent;

  @ViewChild('commandForm')
  commandForm: CommandFormComponent;

  stackOptionsForm: UntypedFormGroup;
  verifierAcknowledgments: AcknowledgmentInfo[] = [];
  extraAcknowledgments: AcknowledgmentInfo[] = [];
  ackOptions: YaSelectOption[] = [
    { id: '', label: 'Inherit' },
    { id: 'Acknowledge_Queued', label: 'Queued', group: true },
    { id: 'Acknowledge_Released', label: 'Released' },
    { id: 'Acknowledge_Sent', label: 'Sent' },
    { id: 'CommandComplete', label: 'Completed' },
  ];

  // Captured in separate subject to avoid referencing
  // the form nested in *ngIf from outside the *ngIf.
  commandFormValid$ = new BehaviorSubject<boolean>(false);

  selectCommandForm: UntypedFormGroup;

  selectedCommand$ = new BehaviorSubject<Command | null>(null);
  templateProvider: StackEntryTemplateProvider | null;

  format: "ycs" | "xml";

  constructor(
    private dialogRef: MatDialogRef<EditStackEntryDialogComponent>,
    readonly yamcs: YamcsService,
    formBuilder: UntypedFormBuilder,
    private changeDetection: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
    let advancementAckDropDownDefault;
    let advancementAckCustomDefault;
    let advancementWaitDefault;
    if (data?.entry) {
      const entry = data.entry as StackEntry;
      this.templateProvider = new StackEntryTemplateProvider(entry);
      this.selectedCommand$.next(entry.command ?? null);

      this.verifierAcknowledgments = [];
      if (entry.command) {
        // Order command definitions top-down
        const commandHierarchy: Command[] = [];
        let c: Command | undefined = entry.command;
        while (c) {
          commandHierarchy.unshift(c);
          c = c.baseCommand;
        }
        for (const command of commandHierarchy) {
          for (const verifier of command.verifier ?? []) {
            this.verifierAcknowledgments.push({ name: `Verifier_${verifier.stage}` });
          }
        }
        let first = true;
        for (const verifier of this.verifierAcknowledgments) {
          this.ackOptions.push({
            id: verifier.name,
            label: verifier.name,
            group: first,
          });
          first = false;
        }
      }

      this.extraAcknowledgments = yamcs.getProcessor()?.acknowledgments ?? [];
      let first = true;
      for (const ack of this.extraAcknowledgments) {
        this.ackOptions.push({
          id: ack.name,
          label: ack.name.replace('Acknowledge_', ''),
          group: first,
        });
        first = false;
      }

      this.ackOptions.push({
        id: 'custom',
        label: 'Custom',
        group: true,
      });

      if (entry.advancement?.acknowledgment) {
        const match = this.ackOptions.find(el => el.id === entry.advancement!.acknowledgment);
        advancementAckDropDownDefault = match ? match.id : 'custom';
      }

      advancementAckCustomDefault = advancementAckDropDownDefault === "custom" ? entry.advancement?.acknowledgment : '';
      advancementWaitDefault = entry.advancement?.wait;
    }
    if (data?.okLabel) {
      this.okLabel = data?.okLabel;
    }
    if (data?.format) {
      this.format = data.format;
    }

    this.stackOptionsForm = formBuilder.group({
      advancementAckDropDown: [advancementAckDropDownDefault || '', []],
      advancementAckCustom: [advancementAckCustomDefault, []],
      advancementWait: [advancementWaitDefault, []],
    });

    if (this.format !== "ycs") {
      this.stackOptionsForm.disable();
    }


    this.selectCommandForm = formBuilder.group({
      command: ['', Validators.required],
    });

    this.selectCommandForm.valueChanges.subscribe(() => {
      const command = this.selectCommandForm.value['command'];
      this.selectedCommand$.next(command || null);
    });

    this.stackOptionsForm.valueChanges.subscribe((result) => {
      if (result.advancementAckDropDown !== 'custom') {
        this.stackOptionsForm.patchValue({ 'advancementAckCustom': undefined }, {
          emitEvent: false,
        });
      }
    });
  }

  handleOK() {
    const stackOptions: { advancement?: AdvancementParams; } = {};
    const advancementAckDropDown = this.stackOptionsForm.get("advancementAckDropDown")?.value;
    const advancementAckCustom = this.stackOptionsForm.get("advancementAckCustom")?.value?.trim();
    const advancementWait = this.stackOptionsForm.get("advancementWait")?.value;

    if ((advancementAckDropDown && advancementAckDropDown !== "custom") ||
      (advancementAckDropDown === "custom" && advancementAckCustom) || advancementWait != null) {
      stackOptions.advancement = {
        ...(advancementAckDropDown && advancementAckDropDown !== "custom" && { acknowledgment: advancementAckDropDown }),
        ...(advancementAckDropDown === "custom" && advancementAckCustom && { acknowledgment: advancementAckCustom }),
        ...(advancementWait != null && { wait: advancementWait })
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
