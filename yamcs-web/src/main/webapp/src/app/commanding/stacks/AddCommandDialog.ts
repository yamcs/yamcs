import { Component, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { ArgumentAssignment, Command, Value } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
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

  @ViewChild('commandForm')
  commandForm: CommandForm;

  selectCommandForm: FormGroup;

  selectedCommand$ = new BehaviorSubject<Command | null>(null);

  constructor(
    private dialogRef: MatDialogRef<AddCommandDialog>,
    readonly yamcs: YamcsService,
    formBuilder: FormBuilder,
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
}
