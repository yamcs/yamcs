import { ChangeDetectionStrategy, Component, Input, OnDestroy, OnInit, Optional, SkipSelf, forwardRef } from '@angular/core';
import { AbstractControl, ControlContainer, FormArrayName, FormControl, FormGroup, FormGroupDirective, FormGroupName, UntypedFormArray, UntypedFormGroup } from '@angular/forms';
import { ArgumentType, NamedObjectId, ParameterSubscription, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription, distinctUntilChanged } from 'rxjs';
import { AggregateArgumentComponent } from '../aggregate-argument/aggregate-argument.component';
import { BinaryArgumentComponent } from '../binary-argument/binary-argument.component';
import { BooleanArgumentComponent } from '../boolean-argument/boolean-argument.component';
import { EnumerationArgumentComponent } from '../enumeration-argument/enumeration-argument.component';
import { FloatArgumentComponent } from '../float-argument/float-argument.component';
import { IntegerArgumentComponent } from '../integer-argument/integer-argument.component';
import { StringArgumentComponent } from '../string-argument/string-argument.component';
import { TimeArgumentComponent } from '../time-argument/time-argument.component';

@Component({
  standalone: true,
  selector: 'app-array-argument',
  templateUrl: './array-argument.component.html',
  styleUrls: ['../arguments.css', './array-argument.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  viewProviders: [{
    provide: ControlContainer,
    useFactory: (formArrayName: FormArrayName, formGroupName: FormGroupName) => {
      return formArrayName || formGroupName;
    },
    deps: [[new SkipSelf(), new Optional(), FormArrayName], FormGroupName],
  }],
  imports: [
    // Break circular imports
    forwardRef(() => AggregateArgumentComponent),
    BinaryArgumentComponent,
    BooleanArgumentComponent,
    EnumerationArgumentComponent,
    FloatArgumentComponent,
    IntegerArgumentComponent,
    WebappSdkModule,
    StringArgumentComponent,
    TimeArgumentComponent,
  ],
})
export class ArrayArgumentComponent implements OnInit, OnDestroy {

  @Input()
  name: string;

  @Input()
  description: string;

  @Input()
  type: ArgumentType;

  @Input()
  index?: number;

  // Set when this array is nested in another array.
  @Input()
  dimensions?: number[];

  // Length of each dimension.
  // If any length is undefined, the array cannot be entered
  ownDimensions: Array<number | undefined> = [];

  @Input()
  initialValue: any[];

  engType$ = new BehaviorSubject<string | null>(null);
  arrayLength$ = new BehaviorSubject<number>(0);
  formArray = new UntypedFormArray([]);

  // Holds initial values for array/aggregate entries.
  // These are passed as @Input rather than control values.
  entryInitialValues: any[] = [];

  private parameterSubscription?: ParameterSubscription;
  private subscriptions: Subscription[] = [];
  private pvalCache: { [key: string]: any; } = {};

  constructor(
    private formGroupName: FormGroupName,
    private yamcs: YamcsService,
    @Optional() private formArrayName: FormArrayName,
  ) {
    // Listen to any argument changes, they
    // may be referenced by a dynamic dimension.
    this.topLevelForm.valueChanges!.subscribe(() => {
      this.updateOwnDimensions();
      this.formArray.updateValueAndValidity({
        onlySelf: true
      });
    });
    this.topLevelForm.statusChanges!.subscribe(() => {
      this.updateOwnDimensions();
      this.formArray.updateValueAndValidity({
        onlySelf: true
      });
    });
  }

  get topLevelForm() {
    return this.formGroupName.formDirective! as FormGroupDirective;
  }

  ngOnInit() {
    const parent = this.formGroupName.control;
    parent.addControl(this.name, this.formArray);
    this.updateOwnDimensions();

    let parameters = this.type.dimensions!
      .filter(dimension => dimension.parameter !== undefined)
      .map(dimension => dimension.parameter.qualifiedName);
    parameters = [...new Set(parameters)];

    if (parameters.length) {
      const ids = parameters.map(parameter => ({ name: parameter }));
      let idMapping: { [key: number]: NamedObjectId; };
      this.parameterSubscription = this.yamcs.yamcsClient.createParameterSubscription({
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor!,
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: false,
        action: 'REPLACE',
      }, data => {
        if (data.mapping) {
          idMapping = {
            ...idMapping,
            ...data.mapping,
          };
        }
        for (const pval of (data.values || [])) {
          if (pval.engValue) {
            const id = idMapping[pval.numericId];
            this.pvalCache[id.name] = utils.convertValue(pval.engValue);
            this.updateOwnDimensions();
          }
        }
      });
    }

    this.formArray.addValidators(() => {
      for (let i = 0; i < this.ownDimensions.length; i++) {
        const dimension = this.ownDimensions[i];
        const dimensionType = this.type.dimensions![i];
        if (dimension !== undefined && dimension < 0) {
          return {
            'invalidDimension': {
              'dimension': dimension,
            }
          };
        }
        if (dimensionType.argument) {
          if (dimension === undefined) {
            return {
              'argumentRequired': {
                'name': dimensionType.argument,
              }
            };
          }
        } else if (dimensionType.parameter) {
          if (dimension === undefined) {
            return {
              'parameterRequired': {
                'qualifiedName': dimensionType.parameter.qualifiedName,
                'name': dimensionType.parameter.name,
              }
            };
          }
        }
      }
      return null;
    });
    this.formArray.updateValueAndValidity();

    if (this.initialValue?.length === this.arrayLength$.value) {
      this.entryInitialValues = [... this.initialValue];
    }

    let first = true;
    this.subscriptions.push(
      this.arrayLength$.pipe(
        distinctUntilChanged(),
      ).subscribe(arrayLength => {
        this.formArray.clear();
        for (let i = 0; i < arrayLength; i++) {
          if (!this.type.elementType!.engType.endsWith('[]') && this.type.elementType!.engType !== 'aggregate') {
            const initialValue = first ? (this.entryInitialValues[i] ?? '') : '';
            this.formArray.setControl(i, new FormControl(initialValue));
          } else if (this.type.elementType!.engType === 'aggregate') {
            this.formArray.setControl(i, new UntypedFormGroup({}));
          } else {
            this.formArray.setControl(i, new UntypedFormArray([]));
          }
        }
      })
    );

    first = false; // Only initialize once
  }

  get label() {
    if (this.index !== undefined) {
      const index = utils.unflattenIndex(this.index, this.dimensions!);
      return index.map(i => '[' + i + ']').join('');
    } else {
      return this.name;
    }
  }

  private updateEngType() {
    let result = this.type.elementType!.engType;

    // Array within array, avoid index confusion
    if (result.endsWith('[]')) {
      result = 'array';
    }

    for (const dim of this.ownDimensions) {
      result += (dim !== undefined) ? `[${dim}]` : '[?]';
    }

    this.engType$.next(result);
  }

  /**
   * Establish dimensions of this array by looking at
   * the latest argument/parameter values, or just
   * a fixed value.
   */
  private updateOwnDimensions() {
    const { dimensions } = this.type;
    if (dimensions) {
      let result = [];
      for (const dimension of dimensions) {
        if (dimension.fixedValue !== undefined) {
          result.push(Number(dimension.fixedValue));
        } else if (dimension.argument) {
          const control = this.findClosestControl(dimension.argument);
          if (control?.valid && control.value !== '') {
            const n = Number(control.value);
            if (isNaN(n)) {
              result.push(undefined);
            } else {
              let transformed = n * Number(dimension.slope);
              transformed += Number(dimension.intercept);
              result.push(transformed);
            }
          } else {
            result.push(undefined);
          }
        } else if (dimension.parameter) {
          const pval = this.pvalCache[dimension.parameter.qualifiedName];
          if (pval !== undefined) {
            const n = Number(pval);
            if (isNaN(n)) {
              result.push(undefined);
            } else {
              let transformed = n * Number(dimension.slope);
              transformed += Number(dimension.intercept);
              result.push(transformed);
            }
          } else {
            result.push(undefined);
          }
        } else {
          result.push(undefined);
        }
      }

      this.ownDimensions = result;
      this.updateEngType();

      let flatLength = 1;
      for (const dimension of this.ownDimensions) {
        if (dimension === undefined) {
          flatLength = 0;
          break;
        } else {
          flatLength *= dimension;
        }
      }
      this.arrayLength$.next(flatLength);
    }
  }

  private findClosestControl(name: string) {
    let parent: AbstractControl<any> | null = this.formGroupName.control;
    while (parent && parent !== this.topLevelForm.control) {
      if (parent instanceof FormGroup) { // Ignore FormArray
        if (name in parent.controls) {
          return parent.controls[name];
        }
      }
      parent = parent.parent;
    }
  }

  ngOnDestroy() {
    this.parameterSubscription?.cancel();
    this.subscriptions.forEach(s => s.unsubscribe());
  }
}
