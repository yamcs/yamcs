import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { SelectOption } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-item-band-styles',
  templateUrl: './item-band-styles.component.html',
  styleUrl: '../../shared/StyleTable.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class ItemBandStylesComponent {

  itemTextOverflowOptions: SelectOption[] = [
    { id: 'show', label: 'Show' },
    { id: 'clip', label: 'Clip' },
    { id: 'hide', label: 'Hide' },
  ];

  @Input()
  form: UntypedFormGroup;
}
