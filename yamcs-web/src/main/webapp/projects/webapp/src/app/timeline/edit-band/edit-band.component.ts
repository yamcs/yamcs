import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, TimelineBand, UpdateTimelineBandRequest, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { EditCommandBandComponent } from '../command-band/edit-command-band/edit-command-band.component';
import { EditItemBandComponent } from '../item-band/edit-item-band/edit-item-band.component';
import { EditSpacerComponent } from '../spacer/edit-spacer/edit-spacer.component';
import { EditTimeRulerComponent } from '../time-ruler/edit-time-ruler/edit-time-ruler.component';

@Component({
  standalone: true,
  templateUrl: './edit-band.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    EditCommandBandComponent,
    EditItemBandComponent,
    EditSpacerComponent,
    EditTimeRulerComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class EditBandComponent implements OnDestroy {

  form: UntypedFormGroup;
  dirty$ = new BehaviorSubject<boolean>(false);

  private formSubscription: Subscription;

  band$: Promise<TimelineBand>;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
    formBuilder: UntypedFormBuilder,
    readonly location: Location,
  ) {
    title.setTitle('Edit Band');
    const id = route.snapshot.paramMap.get('band')!;
    this.band$ = yamcs.yamcsClient.getTimelineBand(yamcs.instance!, id);
    this.band$.then(band => {
      this.form = formBuilder.group({
        name: [band.name, [Validators.required]],
        description: [band.description || ''],
        tags: [band.tags || []],
        properties: formBuilder.group({}), // Properties are added in sub-components
      });
      this.formSubscription = this.form.valueChanges.subscribe(() => {
        this.dirty$.next(true);
      });
    }).catch(err => this.messageService.showError(err));
  }

  doOnConfirm() {
    const id = this.route.snapshot.paramMap.get('band')!;
    const formValue = this.form.value;
    const options: UpdateTimelineBandRequest = {
      name: formValue.name,
      description: formValue.description,
      shared: true,
      tags: formValue.tags,
      properties: formValue.properties,
    };

    this.yamcs.yamcsClient.updateTimelineBand(this.yamcs.instance!, id, options)
      .then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
