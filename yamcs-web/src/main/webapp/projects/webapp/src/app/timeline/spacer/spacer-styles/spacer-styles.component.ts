import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { SharedModule } from '../../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-spacer-styles',
  templateUrl: './spacer-styles.component.html',
  styleUrl: '../../shared/StyleTable.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class SpacerStylesComponent {

  @Input()
  form: UntypedFormGroup;
}
