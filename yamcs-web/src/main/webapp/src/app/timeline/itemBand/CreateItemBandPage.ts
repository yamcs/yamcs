import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { defaultProperties } from './ItemBandStyles';

@Component({
  templateUrl: './CreateItemBandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateItemBandPage {

  form: FormGroup;

  constructor(
    title: Title,
    formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
  ) {
    title.setTitle('Configure Item Band');
    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      properties: formBuilder.group({
        itemBackgroundColor: [defaultProperties.itemBackgroundColor, [Validators.required]],
        itemBorderColor: [defaultProperties.itemBorderColor, [Validators.required]],
        itemBorderWidth: [defaultProperties.itemBorderWidth, [Validators.required]],
        itemCornerRadius: [defaultProperties.itemCornerRadius, [Validators.required]],
        itemHeight: [defaultProperties.itemHeight, [Validators.required]],
        itemMarginLeft: [defaultProperties.itemMarginLeft, [Validators.required]],
        itemTextColor: [defaultProperties.itemTextColor, [Validators.required]],
        itemTextOverflow: [defaultProperties.itemTextOverflow, [Validators.required]],
        itemTextSize: [defaultProperties.itemTextSize, [Validators.required]],
        marginBottom: [defaultProperties.marginBottom, [Validators.required]],
        marginTop: [defaultProperties.marginTop, [Validators.required]],
        multiline: [defaultProperties.multiline, [Validators.required]],
        spaceBetweenItems: [defaultProperties.spaceBetweenItems, [Validators.required]],
        spaceBetweenLines: [defaultProperties.spaceBetweenLines, [Validators.required]],
      })
    });
  }

  onConfirm() {
    const formValue = this.form.value;

    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, {
      name: formValue.name,
      description: formValue.description,
      type: 'ITEM_BAND',
      shared: true,
      tags: [],
      properties: formValue.properties,
    }).then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
