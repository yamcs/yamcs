import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Parameter, ParameterValue } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-parameter-detail',
  templateUrl: './ParameterDetail.html',
  styleUrls: ['./ParameterDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterDetail {

  @Input()
  parameter: Parameter;

  @Input()
  offset: string;

  @Input()
  pval: ParameterValue;

  constructor(readonly yamcs: YamcsService) {
  }
}
