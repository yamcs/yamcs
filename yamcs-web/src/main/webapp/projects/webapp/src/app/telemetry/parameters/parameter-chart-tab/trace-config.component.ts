import { Component, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { EnumValue, WebappSdkModule } from '@yamcs/webapp-sdk';
import { AppParameterInput } from '../../../shared/parameter-input/parameter-input.component';

@Component({
  selector: 'app-trace-config',
  templateUrl: './trace-config.component.html',
  styleUrl: './trace-config.component.css',
  imports: [AppParameterInput, WebappSdkModule],
})
export class TraceConfigComponent {
  @Input()
  form: FormGroup;

  /**
   * Value↔label table shown as a read-only key for an enum-typed engineering
   * trace. Undefined / empty for numeric traces.
   */
  @Input()
  enumValues?: EnumValue[];
}
