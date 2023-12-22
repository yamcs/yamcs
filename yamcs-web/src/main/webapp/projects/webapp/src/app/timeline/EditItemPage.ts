import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, SelectOption, TimelineItem, UpdateTimelineItemRequest, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  templateUrl: './EditItemPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditItemPage implements OnDestroy {

  resolutionOptions: SelectOption[] = [
    { id: 'seconds', label: 'seconds' },
    { id: 'minutes', label: 'minutes' },
    { id: 'hours', label: 'hours' }
  ];
  startConstraintOptions: SelectOption[] = [
    { id: 'START_ON', label: 'Start on' },
  ];

  form: UntypedFormGroup;
  dirty$ = new BehaviorSubject<boolean>(false);

  private formSubscription: Subscription;

  item$: Promise<TimelineItem>;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    formBuilder: UntypedFormBuilder,
    readonly location: Location,
  ) {
    title.setTitle('Edit Item');
    const id = route.snapshot.paramMap.get('item')!;
    this.item$ = yamcs.yamcsClient.getTimelineItem(yamcs.instance!, id);
    this.item$.then(item => {
      this.form = formBuilder.group({
        name: [item.name, [Validators.required]],
        start: [item.start, Validators.required],
        duration: [item.duration, Validators.required],
        tags: [item.tags || [], []],
      });
      this.formSubscription = this.form.valueChanges.subscribe(() => {
        this.dirty$.next(true);
      });
    }).catch(err => this.messageService.showError(err));
  }

  doOnConfirm() {
    const id = this.route.snapshot.paramMap.get('item')!;
    const formValue = this.form.value;
    const options: UpdateTimelineItemRequest = {
      name: formValue.name,
      start: utils.toISOString(formValue.start),
      duration: formValue.duration,
      tags: formValue.tags,
    };

    this.yamcs.yamcsClient.updateTimelineItem(this.yamcs.instance!, id, options)
      .then(() => this.router.navigateByUrl(`/timeline/items?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
