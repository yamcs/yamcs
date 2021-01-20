import { ChangeDetectorRef, Component, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { ArgumentAssignment, Command, Value } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandSelector } from '../../shared/forms/CommandSelector';
import { CommandForm } from '../command-sender/CommandForm';

export interface CommandResult {
  command: Command;
  assignments: ArgumentAssignment[];
  comment?: string;
  extra: { [key: string]: Value; };
}

@Component({
  selector: 'app-add-command-dialog',
  templateUrl: './AddCommandDialog.html',
  styleUrls: ['./AddCommandDialog.css'],
})
export class AddCommandDialog {

  @ViewChild('commandSelector')
  commandSelector: CommandSelector;

  @ViewChild('commandForm')
  commandForm: CommandForm;

  selectCommandForm: FormGroup;

  selectedCommand$ = new BehaviorSubject<Command | null>(null);

  constructor(
    private dialogRef: MatDialogRef<AddCommandDialog>,
    readonly yamcs: YamcsService,
    formBuilder: FormBuilder,
    private changeDetection: ChangeDetectorRef,
  ) {
    this.selectCommandForm = formBuilder.group({
      command: ['', Validators.required],
    });

    this.selectCommandForm.valueChanges.subscribe(() => {
      const command = this.selectCommandForm.value['command'];
      this.selectedCommand$.next(command || null);
    });
  }

  addToStack() {
    const result: CommandResult = {
      command: this.selectedCommand$.value!,
      assignments: this.commandForm.getAssignments(),
      comment: this.commandForm.getComment(),
      extra: this.commandForm.getExtraOptions(),
    };
    this.dialogRef.close(result);
  }

  returnToList(system: string) {
    this.selectedCommand$.next(null);
    this.changeDetection.detectChanges(); // Ensure ngIf resolves and #commandSelector is set
    this.selectCommandForm.reset();
    this.commandSelector.changeSystem(system === '/' ? '' : system);
  }
}
