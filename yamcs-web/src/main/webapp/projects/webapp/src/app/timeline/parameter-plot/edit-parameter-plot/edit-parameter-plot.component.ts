import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { TimelineBand, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { resolveProperties } from '../../shared/properties';
import { createTracePropertyInfo, DEFAULT_COLORS, propertyInfo, resolveTraceProperties } from '../ParameterPlot';
import { ParameterPlotStylesComponent } from '../parameter-plot-styles/parameter-plot-styles.component';
import { TraceStylesComponent } from '../trace-styles/trace-styles.component';

@Component({
  selector: 'app-edit-parameter-plot',
  templateUrl: './edit-parameter-plot.component.html',
  styleUrl: './edit-parameter-plot.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ParameterPlotStylesComponent,
    TraceStylesComponent,
    WebappSdkModule,
  ],
})
export class EditParameterPlotComponent implements AfterViewInit {

  @Input()
  form: FormGroup;

  @Input()
  band: TimelineBand;

  formConfigured$ = new BehaviorSubject<boolean>(false);

  constructor(
    readonly yamcs: YamcsService,
    private changeDetection: ChangeDetectorRef,
    private formBuilder: FormBuilder,
  ) { }

  ngAfterViewInit() {
    const props = resolveProperties(propertyInfo, this.band.properties || {});

    // Angular does not seem to have form.addGroup. So we get creative.
    // The properties sub-group is set in the parent component, and here
    // we append to it in a roundabout way.

    const propConfig: any = {
      frozen: [props.frozen, [Validators.required]],
      height: [props.height, [Validators.required]],
      minimum: [props.minimum, []],
      maximum: [props.maximum, []],
      zeroLineColor: [props.zeroLineColor, [Validators.required]],
      zeroLineWidth: [props.zeroLineWidth, [Validators.required]],
    };

    const propertiesGroup = this.form.get('properties') as FormGroup;
    for (const controlName in propConfig) {
      const config = propConfig[controlName];
      propertiesGroup.addControl(controlName, new FormControl(config[0], config[1]));
    }

    let idx = 1;
    while (true) {
      const tracePropertyInfo = createTracePropertyInfo(idx, '#zzzzzz');
      const traceProperties = resolveTraceProperties(idx, tracePropertyInfo, this.band.properties || {});
      if (!traceProperties.parameter) {
        break;
      }
      idx++;
      const traceForm = this.addTrace();
      traceForm.patchValue(traceProperties);
    }

    this.formConfigured$.next(true);
    this.changeDetection.detectChanges();
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
      lohi: [true, [Validators.required]],
      lohiOpacity: [0.17, [Validators.required]],
      fill: [false, [Validators.required]],
      fillColor: ['#dddddd', [Validators.required]],
    });

    if (index !== undefined) {
      this.traces.insert(index + 1, traceForm);
    } else {
      this.traces.push(traceForm);
    }
    return traceForm;
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
}
