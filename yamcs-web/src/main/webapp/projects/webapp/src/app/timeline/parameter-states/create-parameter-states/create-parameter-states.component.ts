import { TitleCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import {
  BaseComponent,
  CreateTimelineBandRequest,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { CreateBandWizardStepComponent } from '../../create-band-wizard-step/create-band-wizard-step.component';
import { removeUnsetProperties } from '../../shared/properties';
import { ParameterStatesStylesComponent } from '../parameter-states-styles/parameter-states-styles.component';
import { propertyInfo } from '../ParameterStateBand';

@Component({
  selector: 'app-create-parameter-states',
  templateUrl: './create-parameter-states.component.html',
  styleUrl: './create-parameter-states.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateBandWizardStepComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    ParameterStatesStylesComponent,
    TitleCasePipe,
    WebappSdkModule,
  ],
})
export class CreateParameterStatesComponent extends BaseComponent {
  form: FormGroup;

  constructor(
    private formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
  ) {
    super();
    this.setTitle('Configure parameter states');

    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      valueMappings: formBuilder.array([]),
      properties: formBuilder.group({
        frozen: [propertyInfo.frozen.defaultValue, [Validators.required]],
        height: [propertyInfo.height.defaultValue, [Validators.required]],
        parameter: ['', [Validators.required]],
      }),
    });
  }

  get valueMappings() {
    return this.form.controls['valueMappings'] as FormArray;
  }

  addValueMapping() {
    const form = this.formBuilder.group({
      type: ['value', [Validators.required]],
      value: ['', [Validators.required]],
      label: [''],
      color: [''],
    });
    this.valueMappings.push(form);
  }

  addRangeMapping() {
    const form = this.formBuilder.group({
      type: ['range', [Validators.required]],
      start: ['', [Validators.required]],
      end: ['', [Validators.required]],
      label: [''],
      color: [''],
    });
    this.valueMappings.push(form);
  }

  removeMapping(index: number) {
    this.valueMappings.removeAt(index);
  }

  moveUp(index: number) {
    const form = this.valueMappings.at(index);
    this.valueMappings.removeAt(index);
    this.valueMappings.insert(index - 1, form);
  }

  moveDown(index: number) {
    const form = this.valueMappings.at(index);
    this.valueMappings.removeAt(index);
    this.valueMappings.insert(index + 1, form);
  }

  onConfirm() {
    const formValue = this.form.value;

    const options: CreateTimelineBandRequest = {
      name: formValue.name,
      description: formValue.description,
      type: 'PARAMETER_STATES',
      shared: true,
      properties: {},
    };

    for (const key in formValue.properties) {
      const value = formValue.properties[key];
      if (value !== null) {
        options.properties![key] = value;
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
      .createTimelineBand(this.yamcs.instance!, options)
      .then(() =>
        this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`),
      )
      .catch((err) => this.messageService.showError(err));
  }
}
