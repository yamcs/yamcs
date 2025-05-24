import { ChangeDetectionStrategy, Component } from '@angular/core';
import { outputFromObservable } from '@angular/core/rxjs-interop';
import {
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import {
  MessageService,
  SaveTimelineBandRequest,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { map, Observable } from 'rxjs';
import { propertyInfo } from '../bands/item-band/ItemBand';
import { ItemBandStylesComponent } from '../bands/item-band/item-band-styles/item-band-styles.component';

@Component({
  selector: 'app-create-item-band',
  templateUrl: './create-item-band.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ItemBandStylesComponent, WebappSdkModule],
})
export class CreateItemBandComponent {
  /**
   * Emits form valid changes
   */
  validChange = outputFromObservable(this.createStatusObservable());

  form: UntypedFormGroup;

  constructor(
    formBuilder: UntypedFormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      properties: formBuilder.group({
        frozen: [propertyInfo.frozen.defaultValue, [Validators.required]],
        itemBackgroundColor: [
          propertyInfo.itemBackgroundColor.defaultValue,
          [Validators.required],
        ],
        itemBorderColor: [
          propertyInfo.itemBorderColor.defaultValue,
          [Validators.required],
        ],
        itemBorderWidth: [
          propertyInfo.itemBorderWidth.defaultValue,
          [Validators.required],
        ],
        itemCornerRadius: [
          propertyInfo.itemCornerRadius.defaultValue,
          [Validators.required],
        ],
        itemHeight: [
          propertyInfo.itemHeight.defaultValue,
          [Validators.required],
        ],
        itemMarginLeft: [
          propertyInfo.itemMarginLeft.defaultValue,
          [Validators.required],
        ],
        itemTextColor: [
          propertyInfo.itemTextColor.defaultValue,
          [Validators.required],
        ],
        itemTextOverflow: [
          propertyInfo.itemTextOverflow.defaultValue,
          [Validators.required],
        ],
        itemTextSize: [
          propertyInfo.itemTextSize.defaultValue,
          [Validators.required],
        ],
        marginBottom: [
          propertyInfo.marginBottom.defaultValue,
          [Validators.required],
        ],
        marginTop: [propertyInfo.marginTop.defaultValue, [Validators.required]],
        multiline: [propertyInfo.multiline.defaultValue, [Validators.required]],
        spaceBetweenItems: [
          propertyInfo.spaceBetweenItems.defaultValue,
          [Validators.required],
        ],
        spaceBetweenLines: [
          propertyInfo.spaceBetweenLines.defaultValue,
          [Validators.required],
        ],
      }),
      tags: [[], []],
    });
  }

  private createStatusObservable() {
    return new Observable<boolean>((sub) => {
      this.form.statusChanges
        .pipe(map((status) => status === 'VALID'))
        .subscribe(sub);
    });
  }

  createRequest(): SaveTimelineBandRequest {
    const formValue = this.form.value;
    return {
      name: formValue.name,
      description: formValue.description,
      type: 'ITEM_BAND',
      shared: true,
      tags: formValue.tags,
      properties: formValue.properties,
    };
  }
}
