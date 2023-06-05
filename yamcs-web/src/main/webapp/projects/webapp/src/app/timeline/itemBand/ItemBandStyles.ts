import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { SelectOption } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-item-band-styles',
  templateUrl: './ItemBandStyles.html',
  styleUrls: ['../StyleTable.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemBandStyles {

  itemTextOverflowOptions: SelectOption[] = [
    { id: 'show', label: 'Show' },
    { id: 'clip', label: 'Clip' },
    { id: 'hide', label: 'Hide' },
  ];

  @Input()
  form: UntypedFormGroup;
}
