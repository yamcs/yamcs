import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import {
  FormArray,
  FormGroup,
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators,
} from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import {
  MessageService,
  TimelineBand,
  UpdateTimelineBandRequest,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { EditCommandBandComponent } from '../command-band/edit-command-band/edit-command-band.component';
import { EditItemBandComponent } from '../item-band/edit-item-band/edit-item-band.component';
import { EditParameterPlotComponent } from '../parameter-plot/edit-parameter-plot/edit-parameter-plot.component';
import { EditParameterStatesComponent } from '../parameter-states/edit-parameter-states/edit-parameter-states.component';
import { removeUnsetProperties } from '../shared/properties';
import { EditSpacerComponent } from '../spacer/edit-spacer/edit-spacer.component';
import { EditTimeRulerComponent } from '../time-ruler/edit-time-ruler/edit-time-ruler.component';

@Component({
  templateUrl: './edit-band.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    EditCommandBandComponent,
    EditItemBandComponent,
    EditParameterPlotComponent,
    EditParameterStatesComponent,
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
    title.setTitle('Edit band');
    const id = route.snapshot.paramMap.get('band')!;
    this.band$ = yamcs.yamcsClient.getTimelineBand(yamcs.instance!, id);
    this.band$
      .then((band) => {
        this.form = formBuilder.group({
          name: [band.name, [Validators.required]],
          description: [band.description || ''],
          tags: [band.tags || []],
          traces: formBuilder.array([]), // Used by parameter plot
          valueMappings: formBuilder.array([]), // Used by parameter states
          properties: formBuilder.group({}), // Properties are added in sub-components
        });
        this.formSubscription = this.form.valueChanges.subscribe(() => {
          this.dirty$.next(true);
        });
      })
      .catch((err) => this.messageService.showError(err));
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

    for (let i = 0; i < this.traces.length; i++) {
      const traceForm = this.traces.at(i) as FormGroup;
      for (const key in traceForm.controls) {
        const propName = `trace_${i + 1}_${key}`;
        const value = traceForm.controls[key].value;
        options.properties![propName] = value;
      }
    }

    for (let i = 0; i < this.valueMappings.length; i++) {
      const mappingForm = this.valueMappings.at(i) as FormGroup;
      for (const key in mappingForm.controls) {
        const propName = `value_mapping_${i}_${key}`;
        const value = mappingForm.controls[key].value;
        options.properties![propName] = value;
      }
    }

    removeUnsetProperties(options.properties || {});

    this.yamcs.yamcsClient
      .updateTimelineBand(this.yamcs.instance!, id, options)
      .then(() =>
        this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`),
      )
      .catch((err) => this.messageService.showError(err));
  }

  get traces() {
    return this.form.controls['traces'] as FormArray;
  }

  get valueMappings() {
    return this.form.controls['valueMappings'] as FormArray;
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
