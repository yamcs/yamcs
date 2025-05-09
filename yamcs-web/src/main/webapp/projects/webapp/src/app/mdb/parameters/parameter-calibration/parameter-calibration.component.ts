import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import {
  ParameterType,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { ExpressionComponent } from '../../../shared/expression/expression.component';
import { PolynomialPipe } from './polynomial.pipe';

@Component({
  selector: 'app-parameter-calibration',
  templateUrl: './parameter-calibration.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ExpressionComponent, PolynomialPipe, WebappSdkModule],
})
export class ParameterCalibrationComponent {
  @Input()
  ptype: ParameterType;

  @Input()
  relto?: string;

  constructor(readonly yamcs: YamcsService) {}
}
