import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';

@Component({
  selector: 'app-item-styles',
  templateUrl: './ItemStyles.html',
  styleUrl: '../StyleTable.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemStyles {

  @Input()
  form: UntypedFormGroup;
}
