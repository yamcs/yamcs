import { Location } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, Input, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { TimelineBand, UpdateTimelineBandRequest } from '../../client/types/timeline';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { addDefaultProperties } from './ItemBandStyles';

@Component({
  selector: 'app-edit-item-band',
  templateUrl: './EditItemBandComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditItemBandComponent implements AfterViewInit, OnDestroy {

  @Input()
  band: TimelineBand;

  form: FormGroup;

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    private formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
    readonly location: Location,
  ) {
    this.form = this.formBuilder.group({
      name: [null, [Validators.required]],
      description: null,
      properties: this.formBuilder.group({
        itemBackgroundColor: [null, [Validators.required]],
        itemBorderColor: [null, [Validators.required]],
        itemBorderWidth: [null, [Validators.required]],
        itemCornerRadius: [null, [Validators.required]],
        itemHeight: [null, [Validators.required]],
        itemMarginLeft: [null, [Validators.required]],
        itemTextColor: [null, [Validators.required]],
        itemTextOverflow: [null, [Validators.required]],
        itemTextSize: [null, [Validators.required]],
        marginBottom: [null, [Validators.required]],
        marginTop: [null, [Validators.required]],
        multiline: [null, [Validators.required]],
        spaceBetweenItems: [null, [Validators.required]],
        spaceBetweenLines: [null, [Validators.required]],
      })
    });
  }

  ngAfterViewInit() {
    this.form.setValue({
      name: this.band.name,
      description: this.band.description || '',
      properties: addDefaultProperties(this.band.properties || {}),
    });
    this.formSubscription = this.form.valueChanges.subscribe(() => {
      this.dirty$.next(true);
    });
  }

  onConfirm() {
    const formValue = this.form.value;
    const options: UpdateTimelineBandRequest = {
      name: formValue.name,
      description: formValue.description,
      shared: this.band.shared,
      tags: this.band.tags || [],
      properties: formValue.properties,
    };

    this.yamcs.yamcsClient.updateTimelineBand(this.yamcs.instance!, this.band.id, options)
      .then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    if (this.formSubscription) {
      this.formSubscription.unsubscribe();
    }
  }
}
