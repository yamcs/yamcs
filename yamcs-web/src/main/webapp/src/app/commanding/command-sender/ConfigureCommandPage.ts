import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Argument, ArgumentAssignment, Command, CommandHistoryEntry, Instance, Value } from '../../client';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
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
  config: WebsiteConfig;

  command$ = new BehaviorSubject<Command | null>(null);
  commandConfigurationForm = new FormGroup({
    _comment: new FormControl(),
  });
  arguments: Argument[] = [];
  argumentsWithInitial: Argument[] = [];
  showAll$ = new BehaviorSubject<boolean>(false);

  armControl = new FormControl();

  constructor(
    route: ActivatedRoute,
    private router: Router,
    title: Title,
    private messageService: MessageService,
    private yamcs: YamcsService,
    private location: Location,
    configService: ConfigService,
  ) {
    this.instance = yamcs.getInstance();
    this.config = configService.getConfig();

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;

    title.setTitle(`Send a command: ${qualifiedName}`);

    if (this.config.twoStageCommanding) {
      this.commandConfigurationForm.valueChanges.subscribe(() => {
        this.armControl.setValue(false);
      });
      this.commandConfigurationForm.statusChanges.subscribe(() => {
        if (this.commandConfigurationForm.valid) {
          this.armControl.enable();
        } else {
          this.armControl.disable();
        }
      });
    }

    const promises: Promise<any>[] = [
      this.yamcs.getInstanceClient()!.getCommand(qualifiedName),
    ];

    const templateId = route.snapshot.queryParamMap.get('template');
    if (templateId) {
      const promise = this.yamcs.getInstanceClient()!.getCommandHistoryEntry(templateId);
      promises.push(promise);
    }

    Promise.all(promises).then(responses => {
      const command = responses[0];
      let template: CommandHistoryEntry | undefined;
      if (responses.length > 1) {
        template = responses[1];
      }
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
            if (template) {
              const previousValue = this.getPreviousAssignment(template, argument.name);
              if (previousValue !== undefined) {
                if (previousValue === argument.initialValue) {
                  this.argumentsWithInitial.push(argument);
                } else {
                  this.arguments.push(argument);
                }
                continue;
              }
            }

            if (argument.initialValue !== undefined) {
              this.argumentsWithInitial.push(argument);
            } else {
              this.arguments.push(argument);
            }
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
      this.argumentsWithInitial = this.argumentsWithInitial.filter(argument => !assignments.has(argument.name));

      for (const arg of this.arguments) {
        this.addControl(arg, template);
      }
      for (const arg of this.argumentsWithInitial) {
        this.addControl(arg, template);
      }
    });
  }

  private getPreviousAssignment(entry: CommandHistoryEntry, argumentName: string) {
    if (entry.assignment) {
      for (const assignment of entry.assignment) {
        if (assignment.name === argumentName) {
          return this.renderFieldValue(assignment.value);
        }
      }
    }
  }

  private addControl(argument: Argument, template?: CommandHistoryEntry) {
    let initialValue = argument.initialValue;

    if (template) {
      const previousValue = this.getPreviousAssignment(template, argument.name);
      if (previousValue !== undefined) {
        initialValue = previousValue;
      }
    }

    if (initialValue === undefined) {
      initialValue = '';
    }

    this.commandConfigurationForm.addControl(
      argument.name, new FormControl(initialValue, Validators.required));
  }

  goBack() {
    this.location.back();
  }

  sendCommand() {
    this.armControl.setValue(false);

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
      this.router.navigate(['/commanding/report', response.id], {
        queryParams: {
          instance: this.instance.name,
        }
      });
    }).catch(err => {
      this.messageService.showError(err);
    });
  }

  private isArgumentWithInitialValue(argumentName: string) {
    for (const arg of this.argumentsWithInitial) {
      if (arg.name === argumentName) {
        return true;
      }
    }
    return false;
  }

  private renderFieldValue(value: Value) {
    switch (value.type) {
      case 'BOOLEAN':
        return '' + value.booleanValue;
      case 'FLOAT':
        return '' + value.floatValue;
      case 'DOUBLE':
        return '' + value.doubleValue;
      case 'UINT32':
        return '' + value.uint32Value;
      case 'SINT32':
        return '' + value.sint32Value;
      case 'BINARY':
        return utils.convertBase64ToHex(value.binaryValue!);
      case 'ENUMERATED':
      case 'STRING':
      case 'TIMESTAMP':
        return value.stringValue!;
      case 'UINT64':
        return '' + value.uint64Value;
      case 'SINT64':
        return '' + value.sint64Value;
    }
  }
}
