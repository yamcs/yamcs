import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService, YamcsService } from '@yamcs/webapp-sdk';
import { propertyInfo } from './ItemBand';

@Component({
  templateUrl: './CreateItemBandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateItemBandPage {

  form: UntypedFormGroup;

  constructor(
    title: Title,
    formBuilder: UntypedFormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Configure Item Band');
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      properties: formBuilder.group({
        frozen: [propertyInfo.frozen.defaultValue, [Validators.required]],
        itemBackgroundColor: [propertyInfo.itemBackgroundColor.defaultValue, [Validators.required]],
        itemBorderColor: [propertyInfo.itemBorderColor.defaultValue, [Validators.required]],
        itemBorderWidth: [propertyInfo.itemBorderWidth.defaultValue, [Validators.required]],
        itemCornerRadius: [propertyInfo.itemCornerRadius.defaultValue, [Validators.required]],
        itemHeight: [propertyInfo.itemHeight.defaultValue, [Validators.required]],
        itemMarginLeft: [propertyInfo.itemMarginLeft.defaultValue, [Validators.required]],
        itemTextColor: [propertyInfo.itemTextColor.defaultValue, [Validators.required]],
        itemTextOverflow: [propertyInfo.itemTextOverflow.defaultValue, [Validators.required]],
        itemTextSize: [propertyInfo.itemTextSize.defaultValue, [Validators.required]],
        marginBottom: [propertyInfo.marginBottom.defaultValue, [Validators.required]],
        marginTop: [propertyInfo.marginTop.defaultValue, [Validators.required]],
        multiline: [propertyInfo.multiline.defaultValue, [Validators.required]],
        spaceBetweenItems: [propertyInfo.spaceBetweenItems.defaultValue, [Validators.required]],
        spaceBetweenLines: [propertyInfo.spaceBetweenLines.defaultValue, [Validators.required]],
      }),
      tags: [[], []],
    });
  }

  onConfirm() {
    const formValue = this.form.value;
    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: formValue.name,
      description: formValue.description,
      type: 'ITEM_BAND',
      shared: true,
      tags: formValue.tags,
      properties: formValue.properties,
    }).then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
