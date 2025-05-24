import { ChangeDetectionStrategy, Component } from '@angular/core';
import { outputFromObservable } from '@angular/core/rxjs-interop';
import {
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators,
} from '@angular/forms';
import {
  BaseComponent,
  SaveTimelineBandRequest,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { map, Observable } from 'rxjs';

@Component({
  selector: 'app-create-time-ruler',
  templateUrl: './create-time-ruler.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class CreateTimeRulerComponent extends BaseComponent {
  /**
   * Emits form valid changes
   */
  validChange = outputFromObservable(this.createStatusObservable());

  form: UntypedFormGroup;

  constructor(formBuilder: UntypedFormBuilder) {
    super();

    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      properties: formBuilder.group({
        timezone: ['UTC', [Validators.required]],
      }),
    });
  }

  private createStatusObservable() {
    return new Observable<boolean>((sub) => {
      this.form.statusChanges
        .pipe(map((status) => status === 'VALID'))
        .subscribe(sub);
    });
  }

  createRequest(): SaveTimelineBandRequest {
    const formValue = this.form.value;

    return {
      name: formValue.name,
      description: formValue.description,
      type: 'TIME_RULER',
      shared: true,
      properties: formValue.properties,
    };
  }
}
