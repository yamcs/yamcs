import { Component, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { AppParameterInput } from '../../../shared/parameter-input/parameter-input.component';

@Component({
  selector: 'app-trace-config',
  templateUrl: './trace-config.component.html',
  imports: [AppParameterInput, WebappSdkModule],
})
export class TraceConfigComponent {
  @Input()
  form: FormGroup;
}
