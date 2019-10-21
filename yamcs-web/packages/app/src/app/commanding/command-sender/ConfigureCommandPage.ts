import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Argument, ArgumentAssignment, Command, Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../../shared/utils';

@Component({
  templateUrl: './ConfigureCommandPage.html',
  styleUrls: ['./ConfigureCommandPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfigureCommandPage {

  instance: Instance;

  command$ = new BehaviorSubject<Command | null>(null);
  commandConfigurationForm = new FormGroup({
    _comment: new FormControl(),
  });
  arguments: Argument[] = [];
  initialValueCount = 0;
  showAll$ = new BehaviorSubject<boolean>(false);

  constructor(
    route: ActivatedRoute,
    private router: Router,
    private messageService: MessageService,
    private yamcs: YamcsService,
    private location: Location,
  ) {
    this.instance = yamcs.getInstance();

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.yamcs.getInstanceClient()!.getCommand(qualifiedName).then(command => {
      this.command$.next(command);

      // Order command definitions top-down
      const commandHierarchy: Command[] = [];
      let c: Command | undefined = command;
      while (c) {
        commandHierarchy.unshift(c);
        c = c.baseCommand;
      }

      const assignments = new Map<string, string>();
      for (const c of commandHierarchy) {
        if (c.argument) {
          for (const argument of c.argument) {
            this.arguments.push(argument);
          }
        }
        if (c.argumentAssignment) {
          for (const assignment of c.argumentAssignment) {
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
        this.commandConfigurationForm.addControl(
          arg.name, new FormControl(value, Validators.required));
      }
    });
  }

  goBack() {
    this.location.back();
  }

  sendCommand() {
    const assignments: ArgumentAssignment[] = [];
    let comment = null;
    for (const controlName in this.commandConfigurationForm.controls) {
      if (this.commandConfigurationForm.controls.hasOwnProperty(controlName)) {
        if (controlName === '_comment') {
          comment = this.commandConfigurationForm.value['_comment'];
        } else {
          const control = this.commandConfigurationForm.controls[controlName];
          if (!this.isArgumentWithInitialValue(controlName) || control.touched) {
            assignments.push({ name: controlName, value: this.commandConfigurationForm.value[controlName] });
          }
        }
      }
    }

    const processor = this.yamcs.getProcessor();
    const qname = this.command$.value!.qualifiedName;
    this.yamcs.getInstanceClient()!.issueCommand(processor.name, qname, {
      assignment: assignments,
      comment: comment || undefined,
    }).then(response => {
      const id = response.commandQueueEntry.cmdId;
      const idString = utils.printCommandId(id);
      this.router.navigate(['/commanding/report', idString], {
        queryParams: {
          instance: this.instance.name,
        }
      });
    }).catch(err => {
      this.messageService.showError(err);
    });
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
