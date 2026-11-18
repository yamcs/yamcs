import { Component, signal } from '@angular/core';
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
  YaSelectOption,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { map, Observable } from 'rxjs';
import { propertyInfo } from '../bands/item-band/ItemBand';
import { ItemBandStylesComponent } from '../bands/item-band/item-band-styles/item-band-styles.component';

@Component({
  selector: 'app-create-item-band',
  templateUrl: './create-item-band.component.html',
  imports: [ItemBandStylesComponent, WebappSdkModule],
})
export class CreateItemBandComponent {
  /**
   * Emits form valid changes
   */
  validChange = outputFromObservable(this.createStatusObservable());

  form: UntypedFormGroup;

  // The item sources, without "commands" (it has its own band type)
  sourceOptions = signal<YaSelectOption[]>([]);

  constructor(
    formBuilder: UntypedFormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      source: ['rdb', [Validators.required]],
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

    yamcs.yamcsClient
      .getTimelineSources(yamcs.instance!)
      .then((page) => {
        const sources = Object.keys(page.sources || {})
          .filter((source) => source !== 'commands')
          .sort();
        this.sourceOptions.set(
          sources.map((source) => ({ id: source, label: source })),
        );
      })
      .catch((err) => this.messageService.showError(err));
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
      source: formValue.source,
      tags: formValue.tags,
      properties: formValue.properties,
    };
  }
}
