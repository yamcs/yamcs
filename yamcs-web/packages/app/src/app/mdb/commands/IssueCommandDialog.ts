import { Component, Inject } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Argument, ArgumentAssignment, Command } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-issue-command-dialog',
  templateUrl: './IssueCommandDialog.html',
  styleUrls: ['./IssueCommandDialog.css'],
})
export class IssueCommandDialog {

  form = new FormGroup({
    _comment: new FormControl(),
  });
  arguments: Argument[] = [];
  initialValueCount = 0;
  showAll$ = new BehaviorSubject<boolean>(false);
  showComment$ = new BehaviorSubject<boolean>(false);

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
      } else {
        this.initialValueCount++;
      }
      this.form.addControl(arg.name, new FormControl(value, Validators.required));
    }
  }

  issue() {
    const assignments: ArgumentAssignment[] = [];
    let comment = null;
    for (const controlName in this.form.controls) {
      if (this.form.controls.hasOwnProperty(controlName)) {
        if (controlName === '_comment') {
          comment = this.form.value['_comment'];
        } else {
          const control = this.form.controls[controlName];
          if (!this.isArgumentWithInitialValue(controlName) || control.touched) {
            assignments.push({ name: controlName, value: this.form.value[controlName] });
          }
        }
      }
    }
    const processor = this.yamcs.getProcessor();
    this.yamcs.getInstanceClient()!.issueCommand(processor.name, this.data.command.qualifiedName, {
      assignment: assignments,
      comment: comment || undefined,
    }).then(response => this.dialogRef.close(response));
  }

  private isArgumentWithInitialValue(argumentName: string) {
    for (const arg of this.arguments) {
      if (arg.name === argumentName) {
        return arg.initialValue !== undefined;
      }
    }
    return false;
  }
}
