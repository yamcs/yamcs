import { CdkPortalOutlet } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  ContentChild,
  input,
} from '@angular/core';
import { YA_FIELD, YaFieldLabel } from './field-label.directive';

@Component({
  selector: 'ya-field',
  templateUrl: './field.component.html',
  styleUrl: './field.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: YA_FIELD,
      useExisting: YaField,
    },
  ],
  host: {
    class: 'ya-field',
  },
  imports: [CdkPortalOutlet],
})
export class YaField {
  // Plain text label, used when there is no template label
  textLabel = input<string | undefined>(undefined, { alias: 'label' });
  hint = input<string>();

  private _templateLabel: YaFieldLabel;

  // Content for the field label given by `<ng-template ya-field-label>`
  @ContentChild(YaFieldLabel)
  get templateLabel(): YaFieldLabel {
    return this._templateLabel;
  }
  set templateLabel(value: YaFieldLabel | undefined) {
    if (value && value._closestField === this) {
      this._templateLabel = value;
    }
  }
}
