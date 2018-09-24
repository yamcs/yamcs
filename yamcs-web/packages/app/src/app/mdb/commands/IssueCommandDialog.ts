import { Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { Argument, ArgumentAssignment, Command } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-issue-command-dialog',
  templateUrl: './IssueCommandDialog.html',
})
export class IssueCommandDialog {

  form = new FormGroup({});
  arguments: Argument[] = [];

  constructor(
    private dialogRef: MatDialogRef<IssueCommandDialog>,
    private yamcs: YamcsService,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {

    // Order command definitions top-down
    const commandHierarchy: Command[] = [];
    let c: Command | undefined = data.command;
    while (c) {
      commandHierarchy.unshift(c);
      c = c.baseCommand;
    }

    const assignments = new Map<string, string>();
    for (const command of commandHierarchy) {
      if (command.argument) {
        for (const argument of command.argument) {
          this.arguments.push(argument);
        }
      }
      if (command.argumentAssignment) {
        for (const assignment of command.argumentAssignment) {
          assignments.set(assignment.name, assignment.value);
        }
      }
    }

    // Assignments cannot be overriden by user, so filter them out
    this.arguments = this.arguments.filter(argument => !assignments.has(argument.name));

    for (const arg of this.arguments) {
      let value = arg.initialValue;
      if (value === undefined) {
        value = '';
      }
      this.form.addControl(arg.name, new FormControl(value, Validators.required));
    }
  }

  issue() {
    const assignments: ArgumentAssignment[] = [];
    for (const prop in this.form.value) {
      if (this.form.value.hasOwnProperty(prop)) {
        assignments.push({ name: prop, value: this.form.value[prop] });
      }
    }
    const processor = this.yamcs.getProcessor();
    this.yamcs.getInstanceClient()!.issueCommand(processor.name, this.data.command.qualifiedName, {
      assignment: assignments
    }).then(response => this.dialogRef.close(response));
  }
}
