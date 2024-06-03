import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-spacer-styles',
  templateUrl: './spacer-styles.component.html',
  styleUrl: '../../shared/StyleTable.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class SpacerStylesComponent {

  @Input()
  form: UntypedFormGroup;
}
