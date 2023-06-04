import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { Option } from '../../shared/forms/Select';

@Component({
  selector: 'app-item-band-styles',
  templateUrl: './ItemBandStyles.html',
  styleUrls: ['../StyleTable.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemBandStyles {

  itemTextOverflowOptions: Option[] = [
    { id: 'show', label: 'Show' },
    { id: 'clip', label: 'Clip' },
    { id: 'hide', label: 'Hide' },
  ];

  @Input()
  form: UntypedFormGroup;
}
