import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { AggregateValue, Argument, ArgumentAssignment, ArgumentType, Command, CommandHistoryEntry, CommandOption, Member, Value } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService } from '../../core/services/ConfigService';
import { requireFloat, requireInteger } from '../../shared/forms/validators';
import { User } from '../../shared/User';
import * as utils from '../../shared/utils';

/**
 * Returns a flat JavaScript object where keys are stringified initial form values.
 */
function renderAggregateControlValues(prefix: string, value: AggregateValue): { [key: string]: string; } {
  let result: { [key: string]: string; } = {};
  for (let i = 0; i < value.name.length; i++) {
    const entryKey = value.name[i];
    const entryValue = value.value[i];
    if (entryValue.type === 'AGGREGATE') {
      result = { ...result, ...renderAggregateControlValues(prefix + entryKey + '.', entryValue.aggregateValue!) };
    } else {
      result[prefix + entryKey] = renderValue(entryValue)!;
    }
  }
  return result;
}

/**
 * Returns the stringified initial form value for a Value object.
 */
function renderValue(value: Value) {
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

@Component({
  selector: 'app-command-form',
  templateUrl: './CommandForm.html',
  styleUrls: ['./CommandForm.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandForm implements OnChanges {

  private user: User;
  commandOptions: CommandOption[];

  @Input()
  command: Command;

  @Input()
  template: CommandHistoryEntry;

  arguments: Argument[] = [];
  argumentsWithInitial: Argument[] = [];
  showAll$ = new BehaviorSubject<boolean>(false);

  form = new FormGroup({});

  constructor(configService: ConfigService, authService: AuthService) {
    this.user = authService.getUser()!;
    this.commandOptions = configService.getCommandOptions();
    this.form.addControl('extra__comment', new FormControl(''));
    for (const option of this.commandOptions) {
      this.form.addControl('extra__' + option.id, new FormControl(''));
    }
  }

  ngOnChanges() {
    this.arguments = [];
    this.argumentsWithInitial = [];
    this.showAll$.next(false);
    for (const key in this.form.controls) {
      if (!key.startsWith('extra__')) {
        this.form.removeControl(key);
      }
    }
    this.form.reset();

    if (!this.command) {
      return;
    }

    // Order command definitions top-down
    const commandHierarchy: Command[] = [];
    let c: Command | undefined = this.command;
    while (c) {
      commandHierarchy.unshift(c);
      c = c.baseCommand;
    }

    const assignments = new Map<string, string>();
    for (const c of commandHierarchy) {
      if (c.argument) {
        for (const argument of c.argument) {
          if (this.template) {
            const previousValue = this.getPreviousAssignment(this.template, argument.name);
            if (previousValue !== undefined) {
              const stringValue = renderValue(previousValue);
              if (stringValue === argument.initialValue) {
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
      this.addControl(arg, this.template);
    }
    for (const arg of this.argumentsWithInitial) {
      this.addControl(arg, this.template);
    }
  }

  getAssignments(): ArgumentAssignment[] {
    const assignments: ArgumentAssignment[] = [];
    for (const arg of [...this.arguments, ...this.argumentsWithInitial]) {
      if (arg.type.engType === 'aggregate') {
        const jsonValue = JSON.stringify(this.getMemberAssignments(arg.name + '.', arg));
        assignments.push({ name: arg.name, value: jsonValue });
      } else {
        const control = this.form.controls[arg.name];
        if (!this.isArgumentWithInitialValue(arg.name) || control.touched) {
          assignments.push({ name: arg.name, value: this.form.value[arg.name] });
        }
      }
    }
    return assignments;
  }

  getMemberAssignments(prefix: string, argument: Argument | Member): { [key: string]: any; } {
    const result: { [key: string]: any; } = {};
    for (const member of argument.type.member || []) {
      if (member.type.engType === 'aggregate') {
        result[member.name] = this.getMemberAssignments(prefix + member.name + '.', member);
      } else {
        const control = this.form.controls[prefix + member.name];
        switch (member.type.engType) {
          case 'float':
          case 'double':
          case 'integer':
            result[member.name] = Number(control.value);
            break;
          case 'boolean':
            result[member.name] = Boolean(control.value);
            break;
          default:
            result[member.name] = control.value;
        }
      }
    }
    return result;
  }

  getComment(): string | undefined {
    return this.form.value['extra__comment'] || undefined;
  }

  getExtraOptions() {
    const extra: { [key: string]: Value; } = {};
    for (const id in this.form.controls) {
      if (id.startsWith('extra__') && id !== 'extra__comment') {
        const control = this.form.controls[id];
        if (control.value !== null) {
          const optionId = id.replace('extra__', '');
          extra[optionId] = this.toYamcsValue(optionId, control.value);
        }
      }
    }
    return extra;
  }

  private toYamcsValue(optionId: string, controlValue: any): Value {
    let option: CommandOption;
    for (const candidate of this.commandOptions) {
      if (candidate.id === optionId) {
        option = candidate;
      }
    }
    switch (option!.type) {
      case "BOOLEAN":
        if (controlValue === 'true') {
          return { type: "BOOLEAN", booleanValue: true };
        }
        return { type: "BOOLEAN", booleanValue: false };
      case "NUMBER":
        return { type: "SINT32", sint32Value: Number(controlValue) };
      default:
        return { type: "STRING", stringValue: String(controlValue) };
    }
  }

  showCommandOptions() {
    return this.user.hasSystemPrivilege('CommandOptions');
  }

  private getPreviousAssignment(entry: CommandHistoryEntry, argumentName: string) {
    if (entry.assignment) {
      for (const assignment of entry.assignment) {
        if (assignment.name === argumentName) {
          return assignment.value;
        }
      }
    }
  }

  private addControl(argument: Argument, template?: CommandHistoryEntry) {
    if (argument.type.engType === 'aggregate') {
      this.addAggregateControl(argument, template);
    } else {
      let initialValue = argument.initialValue;

      if (template) {
        const previousValue = this.getPreviousAssignment(template, argument.name);
        if (previousValue !== undefined) {
          initialValue = renderValue(previousValue);
        }
      }

      if (initialValue === undefined) {
        initialValue = '';
      }

      const validators = this.getValidatorsForType(argument.type);
      this.form.addControl(
        argument.name, new FormControl(initialValue, validators));
    }
  }

  private addAggregateControl(argument: Argument, template?: CommandHistoryEntry) {
    this.addMemberControls(argument.name + '.', argument.type.member || []);

    // let initialValueJSON = argument.initialValue;
    if (template) {
      const previousValue = this.getPreviousAssignment(template, argument.name);
      if (previousValue !== undefined) {
        if (previousValue.type === 'AGGREGATE') {
          const initialValues = renderAggregateControlValues(argument.name + '.', previousValue.aggregateValue!);
          for (const controlName in initialValues) {
            if (controlName in this.form.controls) {
              this.form.controls[controlName].setValue(initialValues[controlName]);
            }
          }
        }
      }
    }
  }

  private addMemberControls(prefix: string, members: Member[]) {
    for (const member of members) {
      if (member.type.engType === 'aggregate') {
        this.addMemberControls(prefix + '.' + member.name + '.', member.type.member || []);
      } else {
        const controlName = prefix + member.name;
        const validators = this.getValidatorsForType(member.type as ArgumentType);
        this.form.addControl(
          controlName, new FormControl('', validators));
      }
    }
  }

  private getValidatorsForType(type: ArgumentType) {
    const validators = [Validators.required];
    if (type.engType === 'integer') {
      validators.push(requireInteger);
    } else if (type.engType === 'float') {
      validators.push(requireFloat);
    }
    if (type.rangeMax !== undefined) {
      validators.push(Validators.max(type.rangeMax));
    }
    if (type.rangeMin !== undefined) {
      validators.push(Validators.min(type.rangeMin));
    }
    return validators;
  }

  private isArgumentWithInitialValue(argumentName: string) {
    for (const arg of this.argumentsWithInitial) {
      if (arg.name === argumentName) {
        return true;
      }
    }
    return false;
  }
}
