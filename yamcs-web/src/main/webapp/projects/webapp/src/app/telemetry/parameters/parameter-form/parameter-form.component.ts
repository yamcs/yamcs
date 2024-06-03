import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { Parameter, WebappSdkModule } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-parameter-form',
  templateUrl: './parameter-form.component.html',
  styleUrl: './parameter-form.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ParameterFormComponent implements OnInit {

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
