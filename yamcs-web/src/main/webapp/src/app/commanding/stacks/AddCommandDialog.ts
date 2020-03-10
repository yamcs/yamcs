import { Component, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { ArgumentAssignment, Command } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandForm } from '../command-sender/CommandForm';

export interface CommandResult {
  command: Command;
  assignments: ArgumentAssignment[];
  comment?: string;
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
  instance: string;

  selectedCommand$ = new BehaviorSubject<Command | null>(null);

  constructor(
    private dialogRef: MatDialogRef<AddCommandDialog>,
    private yamcs: YamcsService,
    formBuilder: FormBuilder,
  ) {
    this.instance = yamcs.getInstance();
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
    };
    this.dialogRef.close(result);
  }
}
