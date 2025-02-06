import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-parameter-plot-styles',
  templateUrl: './parameter-plot-styles.component.html',
  styleUrl: '../../shared/StyleTable.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ParameterPlotStylesComponent {

  @Input()
  form: FormGroup;
}
