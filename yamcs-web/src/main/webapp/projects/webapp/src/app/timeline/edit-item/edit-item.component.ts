import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { FormGroup, UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, TimelineItem, UpdateTimelineItemRequest, WebappSdkModule, YaSelectOption, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { itemPropertyInfo } from '../item-band/ItemBand';
import { ItemStylesComponent } from '../item-band/item-styles/item-styles.component';
import { resolveProperties } from '../shared/properties';

const OVERRIDE_SUFFIX = '_overrideBand';

@Component({
  standalone: true,
  templateUrl: './edit-item.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    ItemStylesComponent,
    WebappSdkModule,
  ],
})
export class EditItemComponent implements OnDestroy {

  resolutionOptions: YaSelectOption[] = [
    { id: 'seconds', label: 'seconds' },
    { id: 'minutes', label: 'minutes' },
    { id: 'hours', label: 'hours' }
  ];
  startConstraintOptions: YaSelectOption[] = [
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

      const itemProperties = item.properties || {};
      const props = resolveProperties(itemPropertyInfo, itemProperties);

      this.form = formBuilder.group({
        name: [item.name, [Validators.required]],
        start: [item.start, Validators.required],
        duration: [item.duration, Validators.required],
        tags: [item.tags || [], []],
        properties: formBuilder.group({
          backgroundColor: [props.backgroundColor, []],
          backgroundColor_overrideBand: 'backgroundColor' in itemProperties,
          borderColor: [props.borderColor, []],
          borderColor_overrideBand: 'borderColor' in itemProperties,
          borderWidth: [props.borderWidth, []],
          borderWidth_overrideBand: 'borderWidth' in itemProperties,
          cornerRadius: [props.cornerRadius, []],
          cornerRadius_overrideBand: 'cornerRadius' in itemProperties,
          marginLeft: [props.marginLeft, []],
          marginLeft_overrideBand: 'marginLeft' in itemProperties,
          textColor: [props.textColor, []],
          textColor_overrideBand: 'textColor' in itemProperties,
          textSize: [props.textSize, []],
          textSize_overrideBand: 'textSize' in itemProperties,
        }),
      });

      this.updateDisabledState();
      this.formSubscription = this.form.valueChanges.subscribe(() => {
        this.updateDisabledState();
        this.dirty$.next(true);
      });
    }).catch(err => this.messageService.showError(err));
  }

  private updateDisabledState() {
    const { controls } = this.propertiesGroup;
    for (const key in controls) {
      if (key.endsWith(OVERRIDE_SUFFIX)) {
        const styleControl = controls[key.substring(0, key.length - OVERRIDE_SUFFIX.length)];
        if (controls[key].value) {
          styleControl.enable({ onlySelf: true });
        } else {
          styleControl.disable({ onlySelf: true });
        }
      }
    }
  }

  get propertiesGroup(): FormGroup {
    return this.form.controls['properties'] as FormGroup;
  }

  doOnConfirm() {
    const id = this.route.snapshot.paramMap.get('item')!;
    const formValue = this.form.value;

    const options: UpdateTimelineItemRequest = {
      name: formValue.name,
      start: utils.toISOString(formValue.start),
      duration: formValue.duration,
      tags: formValue.tags,
      properties: {},
    };
    if (!options.tags?.length) {
      options.clearTags = true;
    }

    const { controls: propControls } = this.propertiesGroup;
    for (const key in propControls) {
      if (key.endsWith(OVERRIDE_SUFFIX) && propControls[key].value) {
        const propName = key.substring(0, key.length - OVERRIDE_SUFFIX.length);
        options.properties![propName] = propControls[propName].value;
      }
    }
    if (!options.properties?.length) {
      options.clearProperties = true;
    }

    this.yamcs.yamcsClient.updateTimelineItem(this.yamcs.instance!, id, options)
      .then(() => this.router.navigateByUrl(`/timeline/items?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
