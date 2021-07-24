import { Location } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, Input, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { TimelineBand, UpdateTimelineBandRequest } from '../client/types/timeline';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';

@Component({
  selector: 'app-edit-time-ruler',
  templateUrl: './EditTimeRulerComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditTimeRulerComponent implements AfterViewInit, OnDestroy {

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
      timezone: [null, [Validators.required]],
    });
    this.formSubscription = this.form.valueChanges.subscribe(() => {
      this.dirty$.next(true);
    });
  }

  ngAfterViewInit() {
    this.form.setValue({
      name: this.band.name,
      timezone: this.band.properties!['timezone'],
    });
  }

  onConfirm() {
    const formValue = this.form.value;
    const options: UpdateTimelineBandRequest = {
      name: formValue.name,
      shared: this.band.shared,
      tags: this.band.tags || [],
      properties: {
        timezone: formValue.timezone,
      }
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
