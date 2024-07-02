import { ChangeDetectionStrategy, Component, Input, OnInit, Optional, SkipSelf, forwardRef } from '@angular/core';
import { ControlContainer, FormArray, FormArrayName, FormGroup, FormGroupName, UntypedFormControl } from '@angular/forms';
import { ArgumentType, WebappSdkModule, utils } from '@yamcs/webapp-sdk';
import { ArrayArgumentComponent } from '../array-argument/array-argument.component';
import { BinaryArgumentComponent } from '../binary-argument/binary-argument.component';
import { BooleanArgumentComponent } from '../boolean-argument/boolean-argument.component';
import { EnumerationArgumentComponent } from '../enumeration-argument/enumeration-argument.component';
import { FloatArgumentComponent } from '../float-argument/float-argument.component';
import { IntegerArgumentComponent } from '../integer-argument/integer-argument.component';
import { StringArgumentComponent } from '../string-argument/string-argument.component';
import { TimeArgumentComponent } from '../time-argument/time-argument.component';

@Component({
  standalone: true,
  selector: 'app-aggregate-argument',
  templateUrl: './aggregate-argument.component.html',
  styleUrls: ['../arguments.css', './aggregate-argument.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  viewProviders: [{
    provide: ControlContainer,
    useFactory: (formArrayName: FormArrayName, formGroupName: FormGroupName) => {
      return formArrayName || formGroupName;
    },
    deps: [[new SkipSelf(), new Optional(), FormArrayName], FormGroupName],
  }],
  imports: [
    // Break circular imports
    forwardRef(() => ArrayArgumentComponent),
    BinaryArgumentComponent,
    BooleanArgumentComponent,
    EnumerationArgumentComponent,
    FloatArgumentComponent,
    IntegerArgumentComponent,
    WebappSdkModule,
    StringArgumentComponent,
    TimeArgumentComponent,
  ],
})
export class AggregateArgumentComponent implements OnInit {

  @Input()
  name: string;

  @Input()
  description?: string;

  @Input()
  type: ArgumentType;

  @Input()
  index?: number;

  @Input()
  dimensions?: number[];

  @Input()
  initialValue: { [key: string]: any; };

  controlName: string;

  // Holds initial values for array/aggregate members.
  // These are passed as @Input rather than control values.
  memberInitialValues: any[] = [];

  constructor(
    private formGroupName: FormGroupName,
    @Optional() private formArrayName: FormArrayName,
  ) { }

  ngOnInit() {
    const parent = this.formGroupName.control;
    let formGroup: FormGroup;

    if (this.index === undefined) {
      this.controlName = this.name;
      formGroup = new FormGroup({});
      parent.setControl(this.name, formGroup);
    } else {
      this.controlName = String(this.index);
      const index = Number(this.index);
      const formArray = parent.controls[this.name] as FormArray;
      formGroup = formArray.at(index) as FormGroup;
    }

    for (const member of this.type.member || []) {
      let initialValue;
      if (member.type.engType === 'aggregate') {
        initialValue = {};
        if (this.initialValue && this.initialValue.hasOwnProperty(member.name)) {
          initialValue = this.initialValue[member.name];
        }
      } else if (member.type.engType.endsWith('[]')) {
        initialValue = [];
        if (this.initialValue && this.initialValue.hasOwnProperty(member.name)) {
          initialValue = this.initialValue[member.name];
        }
      } else {
        initialValue = member.initialValue ?? '';
        if (this.initialValue && this.initialValue.hasOwnProperty(member.name)) {
          initialValue = this.initialValue[member.name];
        }

        const control = new UntypedFormControl(initialValue);
        formGroup.setControl(member.name, control);
      }

      this.memberInitialValues.push(initialValue);
    }
  }

  get label() {
    if (this.index === undefined) {
      return this.name;
    } else {
      const index = utils.unflattenIndex(this.index, this.dimensions!);
      return index.map(i => '[' + i + ']').join('');
    }
  }
}
