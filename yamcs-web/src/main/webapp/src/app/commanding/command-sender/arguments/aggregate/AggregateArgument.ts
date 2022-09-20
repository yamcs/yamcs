import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { ControlContainer, FormGroup, FormGroupName, UntypedFormControl } from '@angular/forms';
import { ArgumentType } from '../../../../client';
import { unflattenIndex } from '../../../../shared/utils';

@Component({
  selector: 'app-aggregate-argument',
  templateUrl: './AggregateArgument.html',
  styleUrls: ['../arguments.css', './AggregateArgument.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  viewProviders: [{ provide: ControlContainer, useExisting: FormGroupName }],
})
export class AggregateArgument implements OnInit {

  @Input()
  name: string;

  @Input()
  type: ArgumentType;

  @Input()
  index?: number;

  @Input()
  dimensions?: number[];

  @Input()
  initialValue: { [key: string]: any; };

  formGroup = new FormGroup({});

  // Holds initial values for array/aggregate members.
  // These are passed as @Input rather than control values.
  memberInitialValues: any[] = [];

  constructor(
    private formGroupName: FormGroupName
  ) { }

  ngOnInit() {
    const parent = this.formGroupName.control;
    parent.setControl(this.name, this.formGroup);

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
        this.formGroup.setControl(member.name, control);
      }

      this.memberInitialValues.push(initialValue);
    }
  }

  get label() {
    if (this.index === undefined) {
      return this.name;
    } else {
      const index = unflattenIndex(this.index, this.dimensions!);
      return index.map(i => '[' + i + ']').join('');
    }
  }
}
