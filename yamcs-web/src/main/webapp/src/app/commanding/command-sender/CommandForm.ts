import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { AggregateValue, Argument, ArgumentType, Command, CommandOption, Member, Value } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
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

export interface TemplateProvider {
  getAssignment(name: string): Value | void;
  getComment(): string | void;
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
  templateProvider: TemplateProvider;

  arguments: Argument[] = [];
  argumentsWithInitial: Argument[] = [];
  showAll$ = new BehaviorSubject<boolean>(false);

  form = new FormGroup({});
  config: WebsiteConfig;

  constructor(configService: ConfigService, authService: AuthService) {
    this.user = authService.getUser()!;
    this.commandOptions = configService.getCommandOptions();
    this.form.addControl('extra__comment', new FormControl(''));
    for (const option of this.commandOptions) {
      this.form.addControl('extra__' + option.id, new FormControl(''));
    }
    this.config = configService.getConfig();
    this.showAll$.next(!this.config.collapseInitializedArguments);
  }

  ngOnChanges() {
    this.arguments = [];
    this.argumentsWithInitial = [];
    this.showAll$.next(!this.config.collapseInitializedArguments);
    for (const key in this.form.controls) {
      if (!key.startsWith('extra__')) {
        this.form.removeControl(key);
      }
    }
    this.form.reset();

    const comment = this.templateProvider?.getComment() || '';
    this.form.controls['extra__comment'].setValue(comment);

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
          if (this.templateProvider) {
            const previousValue = this.templateProvider.getAssignment(argument.name);
            if (previousValue !== undefined) {
              const stringValue = renderValue(previousValue);
              let initialValue = argument.initialValue;
              if (argument.type.engType === 'boolean') {
                initialValue = '' + (argument.type.oneStringValue === initialValue);
              }
              if (stringValue === initialValue) {
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
      this.addControl(arg, this.templateProvider);
    }
    for (const arg of this.argumentsWithInitial) {
      this.addControl(arg, this.templateProvider);
    }
  }

  getAssignments(): { [key: string]: any; } {
    const assignments: { [key: string]: any; } = {};
    for (const arg of [...this.arguments, ...this.argumentsWithInitial]) {
      if (arg.type.engType === 'aggregate') {
        const value = this.getMemberAssignments(arg.name + '.', arg);
        assignments[arg.name] = value;
      } else {
        const control = this.form.controls[arg.name];
        if (!this.isArgumentWithInitialValue(arg.name) || control.dirty) {
          if (arg.type.engType === 'boolean') {
            assignments[arg.name] = (this.form.value[arg.name] === 'true');
          } else {
            // String is better at representing large numbers or precision
            // to the server. Some inputs (hex) store a non-string value,
            // so convert it here.
            assignments[arg.name] = String(this.form.value[arg.name]);
          }
        }
      }
    }
    return assignments;
  }

  getMemberAssignments(prefix: string, argument: Argument | Member) {
    const result: { [key: string]: any; } = {};
    for (const member of argument.type.member || []) {
      if (member.type.engType === 'aggregate') {
        result[member.name] = this.getMemberAssignments(prefix + member.name + '.', member);
      } else {
        const control = this.form.controls[prefix + member.name];
        switch (member.type.engType) {
          case 'boolean':
            result[member.name] = (control.value === 'true');
          case 'float':
          case 'double':
          case 'integer':
            result[member.name] = Number(control.value);
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

  private addControl(argument: Argument, templateProvider: TemplateProvider) {
    if (argument.type.engType === 'aggregate') {
      this.addAggregateControl(argument, templateProvider);
    } else {
      let initialValue;
      if (argument.type.engType === 'boolean' && argument.initialValue) {
        initialValue = '' + (argument.initialValue === argument.type.oneStringValue);
      } else {
        initialValue = argument.initialValue;
      }

      if (templateProvider) {
        const previousValue = templateProvider.getAssignment(argument.name);
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

  private addAggregateControl(argument: Argument, templateProvider?: TemplateProvider) {
    this.addMemberControls(argument.name + '.', argument.type.member || []);

    // let initialValueJSON = argument.initialValue;
    if (templateProvider) {
      const previousValue = templateProvider.getAssignment(argument.name);
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
        this.addMemberControls(prefix + member.name + '.', member.type.member || []);
      } else {
        const controlName = prefix + member.name;
        const validators = this.getValidatorsForType(member.type as ArgumentType);
        this.form.addControl(
          controlName, new FormControl('', validators));
      }
    }
  }

  private getValidatorsForType(type: ArgumentType) {
    const validators = [];
    if (type.minChars !== 0) {
      validators.push(Validators.required);
    }
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
    if (type.minChars !== undefined) {
      validators.push(Validators.minLength(type.minChars));
    }
    if (type.maxChars !== undefined) {
      validators.push(Validators.maxLength(type.maxChars));
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
