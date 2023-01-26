import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';

@Component({
  selector: 'app-spacer-styles',
  templateUrl: './SpacerStyles.html',
  styleUrls: ['../StyleTable.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpacerStyles {

  @Input()
  form: UntypedFormGroup;
}
