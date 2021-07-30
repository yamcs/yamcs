import { Location } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { TimelineBand, UpdateTimelineBandRequest } from '../../client/types/timeline';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { addDefaultSpacerProperties } from './SpacerStyles';

@Component({
  selector: 'app-edit-spacer',
  templateUrl: './EditSpacerComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditSpacerComponent implements AfterViewInit, OnDestroy {

  @Input()
  band: TimelineBand;

  @Output()
  onConfirm = new EventEmitter<TimelineBand>();

  @Output()
  onCancel = new EventEmitter<void>();

  form: FormGroup;

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    private formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private router: Router,
    readonly location: Location,
    private changeDetection: ChangeDetectorRef,
  ) {
    this.form = this.formBuilder.group({
      name: null,
      description: null,
      properties: this.formBuilder.group({
        height: [null, [Validators.required]],
      }),
    });
  }

  ngAfterViewInit() {
    this.form.setValue({
      name: this.band.name || '',
      description: this.band.description || '',
      properties: addDefaultSpacerProperties(this.band.properties || {}),
    });
    this.changeDetection.detectChanges();
    this.formSubscription = this.form.valueChanges.subscribe(() => {
      this.dirty$.next(true);
    });
  }

  doOnConfirm() {
    const formValue = this.form.value;
    const options: UpdateTimelineBandRequest = {
      name: formValue.name,
      description: formValue.description,
      shared: this.band.shared,
      tags: this.band.tags || [],
      properties: formValue.properties,
    };
    this.yamcs.yamcsClient.updateTimelineBand(this.yamcs.instance!, this.band.id, options)
      .then(band => this.onConfirm.emit(band))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    if (this.formSubscription) {
      this.formSubscription.unsubscribe();
    }
  }
}
