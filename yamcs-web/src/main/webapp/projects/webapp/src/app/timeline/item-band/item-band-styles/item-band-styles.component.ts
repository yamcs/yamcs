import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { WebappSdkModule, YaSelectOption } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-item-band-styles',
  templateUrl: './item-band-styles.component.html',
  styleUrl: '../../shared/StyleTable.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ItemBandStylesComponent {

  itemTextOverflowOptions: YaSelectOption[] = [
    { id: 'show', label: 'Show' },
    { id: 'clip', label: 'Clip' },
    { id: 'hide', label: 'Hide' },
  ];

  @Input()
  form: UntypedFormGroup;
}
