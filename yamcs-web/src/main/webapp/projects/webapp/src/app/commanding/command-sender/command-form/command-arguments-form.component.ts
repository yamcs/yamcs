import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { FormArray, FormControl, FormGroup, UntypedFormControl } from '@angular/forms';
import { Argument, ArgumentMember, ArgumentType, Command, ConfigService, WebappSdkModule, WebsiteConfig } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { ArgumentComponent, renderValue } from '../arguments/argument/argument.component';
import { TemplateProvider } from './TemplateProvider';

@Component({
  standalone: true,
  selector: 'app-command-arguments-form',
  templateUrl: './command-arguments-form.component.html',
  styleUrl: './command-arguments-form.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ArgumentComponent,
    WebappSdkModule,
  ],
})
export class CommandArgumentsForm implements OnChanges {

  @Input()
  formGroup: FormGroup;

  @Input()
  command: Command;

  @Input()
  templateProvider: TemplateProvider;

  arguments: Argument[] = [];
  argumentsWithInitial: Argument[] = [];
  showAll$ = new BehaviorSubject<boolean>(false);
  hasArguments$ = new BehaviorSubject<boolean>(false);

  config: WebsiteConfig;

  constructor(configService: ConfigService) {
    this.config = configService.getConfig();
    this.showAll$.next(!this.config.collapseInitializedArguments);
  }

  ngOnChanges(): void {
    if (!this.formGroup.contains('args')) {
      this.formGroup.addControl('args', new FormGroup({}));
    }

    this.arguments = [];
    this.argumentsWithInitial = [];
    this.showAll$.next(!this.config.collapseInitializedArguments);
    for (const key in this.argsGroup.controls) {
      this.argsGroup.removeControl(key);
    }
    this.argsGroup.reset();

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
              if (argument.type.engType === 'boolean' && initialValue !== undefined) {
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

          let initialized = (argument.initialValue !== undefined);
          if (initialized && argument.type.engType === 'aggregate') {
            const aggregateValue = JSON.parse(argument.initialValue!);
            initialized = this.isAggregateFullyInitialized(argument, aggregateValue);
          }

          if (initialized) {
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

    this.hasArguments$.next(this.arguments.length > 0 || this.argumentsWithInitial.length > 0);
  }

  get argsGroup() {
    return this.formGroup.get('args') as FormGroup;
  }

  private addControl(argument: Argument, templateProvider: TemplateProvider) {
    if (argument.type.engType === 'aggregate') {
      // this.argsGroup.addControl(argument.name, new UntypedFormGroup({}));
      // this.addAggregateControl(argument, templateProvider);
    } else if (argument.type.engType.endsWith('[]')) {
      // this.argsGroup.addControl(argument.name, new UntypedFormArray([]));
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

      this.argsGroup.addControl(
        argument.name, new UntypedFormControl(initialValue));
    }
  }

  private isArgumentWithInitialValue(argumentName: string) {
    for (const arg of this.argumentsWithInitial) {
      if (arg.name === argumentName) {
        return true;
      }
    }
    return false;
  }

  private isAggregateFullyInitialized(argument: Argument | ArgumentMember, value: { [key: string]: any; }) {
    for (const member of argument.type.member || []) {
      if (!value.hasOwnProperty(member.name) && argument.initialValue === undefined) {
        return false;
      } else if (member.type.engType === 'aggregate') {
        const subValue: { [key: string]: any; } = value[member.name];
        if (!this.isAggregateFullyInitialized(member, subValue)) {
          return false;
        }
      }
    }
    return true;
  }

  getResult(): { [key: string]: any; } {
    const assignments: { [key: string]: any; } = {};
    for (const arg of [...this.arguments, ...this.argumentsWithInitial]) {
      if (arg.type.engType === 'aggregate') {
        const subform = this.argsGroup.get(arg.name) as FormGroup;
        assignments[arg.name] = this.getMemberAssignments(subform, arg.type);
      } else if (arg.type.engType.endsWith('[]')) {
        const subarray = this.argsGroup.get(arg.name) as FormArray;
        assignments[arg.name] = this.getArrayAssignment(subarray, arg.type.elementType!);
      } else {
        const control = this.argsGroup.controls[arg.name];
        if (!this.isArgumentWithInitialValue(arg.name) || control.dirty) {
          if (arg.type.engType === 'boolean') {
            assignments[arg.name] = (this.argsGroup.value[arg.name] === 'true');
          } else {
            // String is better at representing large numbers or precision
            // to the server. Some inputs (hex) store a non-string value,
            // so convert it here.
            assignments[arg.name] = String(this.argsGroup.value[arg.name]);
          }
        }
      }
    }
    return assignments;
  }

  private getMemberAssignments(form: FormGroup, argumentType: ArgumentType) {
    const result: { [key: string]: any; } = {};
    for (const member of argumentType.member || []) {
      if (member.type.engType === 'aggregate') {
        const subform = form.get(member.name) as FormGroup;
        result[member.name] = this.getMemberAssignments(subform, member.type);
      } else if (member.type.engType.endsWith('[]')) {
        const subarray = form.get(member.name) as FormArray;
        result[member.name] = this.getArrayAssignment(subarray, member.type.elementType!);
      } else if (member.type.engType === 'boolean') {
        const control = form.get(member.name) as FormControl;
        result[member.name] = (control.value === 'true');
      } else {
        const control = form.get(member.name) as FormControl;
        result[member.name] = control.value;
      }
    }
    return result;
  }

  getArrayAssignment(array: FormArray, elementType: ArgumentType) {
    let result: { [key: string]: any; } = [];
    for (const control of array.controls) {
      if (elementType.engType === 'aggregate') {
        const subform = control as FormGroup;
        result.push(this.getMemberAssignments(subform, elementType));
      } else if (elementType.engType.endsWith('[]')) {
        const subarray = control as FormArray;
        result.push(this.getArrayAssignment(subarray, elementType));
      } else if (elementType.engType === 'boolean') {
        result.push(control.value === 'true');
      } else {
        result.push(control.value);
      }
    }
    return result;
  }
}
