import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { AppParameterInput } from '../../../shared/parameter-input/parameter-input.component';

@Component({
  selector: 'app-trace-styles',
  templateUrl: './trace-styles.component.html',
  styleUrl: '../../shared/StyleTable.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppParameterInput,
    WebappSdkModule,
  ],
})
export class TraceStylesComponent {

  @Input()
  form: FormGroup;
}
