import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ParameterType, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { PolynomialPipe } from './polynomial.pipe';

@Component({
  standalone: true,
  selector: 'app-parameter-calibration',
  templateUrl: './parameter-calibration.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PolynomialPipe,
    WebappSdkModule,
  ],
})
export class ParameterCalibrationComponent {

  @Input()
  ptype: ParameterType;

  constructor(readonly yamcs: YamcsService) {
  }
}
