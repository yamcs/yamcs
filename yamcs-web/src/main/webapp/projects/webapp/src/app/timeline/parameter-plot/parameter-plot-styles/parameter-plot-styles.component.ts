import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-parameter-plot-styles',
  templateUrl: './parameter-plot-styles.component.html',
  styleUrls: [
    './parameter-plot-styles.component.css',
    '../../shared/StyleTable.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ParameterPlotStylesComponent {
  @Input()
  form: FormGroup;
}
