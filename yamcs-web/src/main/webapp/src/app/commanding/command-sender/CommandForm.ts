import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { Argument, ArgumentAssignment, Command, CommandHistoryEntry, Value } from '../../client';
import * as utils from '../../shared/utils';

@Component({
    selector: 'app-command-form',
    templateUrl: './CommandForm.html',
    styleUrls: ['./CommandForm.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandForm implements OnChanges {

    @Input()
    command: Command;

    @Input()
    template: CommandHistoryEntry;

    arguments: Argument[] = [];
    argumentsWithInitial: Argument[] = [];
    showAll$ = new BehaviorSubject<boolean>(false);

    form = new FormGroup({
        _comment: new FormControl(),
    });

    ngOnChanges() {
        this.arguments = [];
        this.argumentsWithInitial = [];
        this.showAll$.next(false);
        for (const key in this.form.controls) {
            if (key !== '_comment') {
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
            this.addControl(arg, this.template);
        }
        for (const arg of this.argumentsWithInitial) {
            this.addControl(arg, this.template);
        }
    }

    getAssignments(): ArgumentAssignment[] {
        const assignments: ArgumentAssignment[] = [];
        for (const controlName in this.form.controls) {
            if (this.form.controls.hasOwnProperty(controlName)) {
                if (controlName !== '_comment') {
                    const control = this.form.controls[controlName];
                    if (!this.isArgumentWithInitialValue(controlName) || control.touched) {
                        assignments.push({ name: controlName, value: this.form.value[controlName] });
                    }
                }
            }
        }
        return assignments;
    }

    getComment(): string | undefined {
        return this.form.value['_comment'] || undefined;
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

        this.form.addControl(
            argument.name, new FormControl(initialValue, Validators.required));
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

    private isArgumentWithInitialValue(argumentName: string) {
        for (const arg of this.argumentsWithInitial) {
            if (arg.name === argumentName) {
                return true;
            }
        }
        return false;
    }
}
