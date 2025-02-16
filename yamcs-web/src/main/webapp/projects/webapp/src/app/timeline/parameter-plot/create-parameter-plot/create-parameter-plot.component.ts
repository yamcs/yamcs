import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { BaseComponent, CreateTimelineBandRequest, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { CreateBandWizardStepComponent } from '../../create-band-wizard-step/create-band-wizard-step.component';
import { removeUnsetProperties } from '../../shared/properties';
import { DEFAULT_COLORS, propertyInfo } from '../ParameterPlot';
import { ParameterPlotStylesComponent } from '../parameter-plot-styles/parameter-plot-styles.component';
import { TraceStylesComponent } from '../trace-styles/trace-styles.component';

@Component({
  selector: 'app-create-parameter-plot',
  templateUrl: './create-parameter-plot.component.html',
  styleUrl: './create-parameter-plot.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CreateBandWizardStepComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    ParameterPlotStylesComponent,
    TraceStylesComponent,
    WebappSdkModule,
  ],
})
export class CreateParameterPlotComponent extends BaseComponent {

  form: FormGroup;

  constructor(
    private formBuilder: FormBuilder,
    readonly yamcs: YamcsService,
  ) {
    super();
    this.setTitle('Configure parameter plot');

    this.form = formBuilder.group({
      name: ['', [Validators.required]],
      description: '',
      traces: formBuilder.array([]),
      properties: formBuilder.group({
        frozen: [propertyInfo.frozen.defaultValue, [Validators.required]],
        height: [propertyInfo.height.defaultValue, [Validators.required]],
        minimum: [],
        maximum: [],
        zeroLineWidth: [propertyInfo.zeroLineWidth.defaultValue, [Validators.required]],
        zeroLineColor: [propertyInfo.zeroLineColor.defaultValue, [Validators.required]],
        minimumFractionDigits: [propertyInfo.minimumFractionDigits.defaultValue, [Validators.required]],
        maximumFractionDigits: [propertyInfo.maximumFractionDigits.defaultValue, [Validators.required]],
      }),
    });
    this.addTrace();
  }

  get traces() {
    return this.form.controls['traces'] as FormArray;
  }

  addTrace(index?: number) {
    const lookupIndex = index === undefined ? 0 : (index + 1);
    const hexColor = DEFAULT_COLORS[lookupIndex % DEFAULT_COLORS.length];
    const traceForm = this.formBuilder.group({
      parameter: ['', [Validators.required]],
      lineColor: [hexColor, [Validators.required]],
      visible: [true, [Validators.required]],
      lineWidth: [1, [Validators.required]],
      minMax: [true, [Validators.required]],
      minMaxOpacity: [0.17, [Validators.required]],
      fill: [false, [Validators.required]],
      fillColor: ['#dddddd', [Validators.required]],
    });

    if (index !== undefined) {
      this.traces.insert(index + 1, traceForm);
    } else {
      this.traces.push(traceForm);
    }
  }

  removeTrace(index: number) {
    this.traces.removeAt(index);
  }

  moveUp(index: number) {
    const traceForm = this.traces.at(index);
    this.traces.removeAt(index);
    this.traces.insert(index - 1, traceForm);
  }

  moveDown(index: number) {
    const traceForm = this.traces.at(index);
    this.traces.removeAt(index);
    this.traces.insert(index + 1, traceForm);
  }

  onConfirm() {
    const formValue = this.form.value;

    const options: CreateTimelineBandRequest = {
      name: formValue.name,
      description: formValue.description,
      type: 'PARAMETER_PLOT',
      shared: true,
      properties: {},
    };

    for (const key in formValue.properties) {
      const value = formValue.properties[key];
      if (value !== null) {
        options.properties![key] = value;
      }
    }

    for (let i = 0; i < this.traces.length; i++) {
      const traceForm = this.traces.at(i) as FormGroup;
      for (const key in traceForm.controls) {
        const propName = `trace_${i + 1}_${key}`;
        const value = traceForm.controls[key].value;
        options.properties![propName] = value;
      }
    }

    removeUnsetProperties(options.properties || {});

    this.yamcs.yamcsClient.createTimelineBand(this.yamcs.instance!, options).then(() => this.router.navigateByUrl(`/timeline/bands?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }
}
