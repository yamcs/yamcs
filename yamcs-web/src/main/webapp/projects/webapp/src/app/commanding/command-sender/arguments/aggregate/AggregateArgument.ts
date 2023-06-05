import { ChangeDetectionStrategy, Component, Input, OnInit, Optional, SkipSelf } from '@angular/core';
import { ControlContainer, FormArray, FormArrayName, FormGroup, FormGroupName, UntypedFormControl } from '@angular/forms';
import { ArgumentType, utils } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-aggregate-argument',
  templateUrl: './AggregateArgument.html',
  styleUrls: ['../arguments.css', './AggregateArgument.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  viewProviders: [{
    provide: ControlContainer,
    useFactory: (formArrayName: FormArrayName, formGroupName: FormGroupName) => {
      return formArrayName || formGroupName;
    },
    deps: [[new SkipSelf(), new Optional(), FormArrayName], FormGroupName],
  }],
})
export class AggregateArgument implements OnInit {

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

    for (const member of this.type.member) {
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
