import { ChangeDetectionStrategy, Component } from '@angular/core';
import { outputFromObservable } from '@angular/core/rxjs-interop';
import {
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import {
  MessageService,
  SaveTimelineBandRequest,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { map, Observable } from 'rxjs';

@Component({
  selector: 'app-create-command-band',
  templateUrl: './create-command-band.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class CreateCommandBandComponent {
  /**
   * Emits form valid changes
   */
  validChange = outputFromObservable(this.createStatusObservable());

  form: UntypedFormGroup;

  constructor(
    formBuilder: UntypedFormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      properties: formBuilder.group({}),
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
      type: 'COMMAND_BAND',
      shared: true,
      properties: formValue.properties,
    };
  }
}
