import { CdkPortalOutlet } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  ContentChild,
  input,
} from '@angular/core';
import { YA_ATTR, YaAttrLabel } from './attr-label.directive';

@Component({
  selector: 'ya-attr',
  templateUrl: './attr.component.html',
  styleUrl: './attr.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: YA_ATTR,
      useExisting: YaAttr,
    },
  ],
  host: {
    class: 'ya-attr',
  },
  imports: [CdkPortalOutlet],
})
export class YaAttr {
  // Plain text label, used when there is no template label
  textLabel = input<string | undefined>(undefined, { alias: 'label' });

  private _templateLabel: YaAttrLabel;

  // Content for the attr label given by `<ng-template ya-attr-label>`
  @ContentChild(YaAttrLabel)
  get templateLabel(): YaAttrLabel {
    return this._templateLabel;
  }
  set templateLabel(value: YaAttrLabel | undefined) {
    if (value && value._closestAttr === this) {
      this._templateLabel = value;
    }
  }
}
