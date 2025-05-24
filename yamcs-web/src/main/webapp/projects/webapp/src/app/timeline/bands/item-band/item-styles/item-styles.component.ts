import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-item-styles',
  templateUrl: './item-styles.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ItemStylesComponent {
  @Input()
  form: UntypedFormGroup;
}
