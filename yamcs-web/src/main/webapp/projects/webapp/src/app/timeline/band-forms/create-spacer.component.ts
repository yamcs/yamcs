import { ChangeDetectionStrategy, Component } from '@angular/core';
import { outputFromObservable } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import {
  BaseComponent,
  SaveTimelineBandRequest,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { map, Observable } from 'rxjs';
import { propertyInfo } from '../bands/spacer/Spacer';
import { SpacerStylesComponent } from '../bands/spacer/spacer-styles/spacer-styles.component';

@Component({
  selector: 'app-create-spacer',
  templateUrl: './create-spacer.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SpacerStylesComponent, WebappSdkModule],
})
export class CreateSpacerComponent extends BaseComponent {
  /**
   * Emits form valid changes
   */
  validChange = outputFromObservable(this.createStatusObservable());

  form: FormGroup;

  constructor(formBuilder: FormBuilder) {
    super();
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      properties: formBuilder.group({
        height: [propertyInfo.height.defaultValue, [Validators.required]],
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
      type: 'SPACER',
      shared: true,
      properties: formValue.properties,
    };
  }
}
