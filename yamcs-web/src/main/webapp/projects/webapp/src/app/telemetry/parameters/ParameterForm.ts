import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { Parameter } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-parameter-form',
  templateUrl: './ParameterForm.html',
  styleUrls: ['./ParameterForm.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterForm implements OnInit {

  @Input()
  formGroup: UntypedFormGroup;

  @Input()
  parameter: Parameter;

  @Input()
  parent: string;

  controlName$ = new BehaviorSubject<string | null>(null);

  ngOnInit() {
    if (this.parent) {
      this.controlName$.next(this.parent + '.' + this.parameter.name);
    } else {
      this.controlName$.next(this.parameter.name);
    }
  }
}
