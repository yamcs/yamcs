import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, TimelineBand, UpdateTimelineViewRequest, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { BandMultiSelectComponent } from '../shared/band-multi-select/band-multi-select.component';

@Component({
  standalone: true,
  templateUrl: './edit-view.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BandMultiSelectComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class EditViewComponent implements OnDestroy {

  form: UntypedFormGroup;

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    readonly location: Location,
    formBuilder: UntypedFormBuilder,
  ) {
    title.setTitle('Edit View');
    const id = route.snapshot.paramMap.get('view')!;
    this.form = formBuilder.group({
      name: [null, Validators.required],
      bands: [null, []],
    });
    yamcs.yamcsClient.getTimelineView(yamcs.instance!, id)
      .then(view => {
        this.form.setValue({
          name: view.name,
          bands: view.bands || [],
        });
        this.formSubscription = this.form.valueChanges.subscribe(() => {
          this.dirty$.next(true);
        });
      }).catch(err => this.messageService.showError(err));
  }

  onConfirm() {
    const formValue = this.form.value;
    const options: UpdateTimelineViewRequest = {
      name: formValue.name,
      bands: formValue.bands.map((band: TimelineBand) => band.id),
    };
    const id = this.route.snapshot.paramMap.get('view')!;
    this.yamcs.yamcsClient.updateTimelineView(this.yamcs.instance!, id, options)
      .then(() => this.router.navigateByUrl(`/timeline/views?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
