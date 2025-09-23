import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  forwardRef,
  input,
} from '@angular/core';
import {
  ControlValueAccessor,
  FormControl,
  NG_VALUE_ACCESSOR,
} from '@angular/forms';
import {
  Parameter,
  utils,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { debounceTime, Observable, switchMap, tap } from 'rxjs';

@Component({
  selector: 'app-parameter-input',
  templateUrl: './parameter-input.component.html',
  styleUrl: './parameter-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AppParameterInput),
      multi: true,
    },
  ],
  imports: [WebappSdkModule],
})
export class AppParameterInput implements ControlValueAccessor {
  fill = input(false, { transform: booleanAttribute });

  private onChange = (_: string | null) => {};

  formControl = new FormControl<string | null>('');
  filteredOptions: Observable<Parameter[]>;

  constructor(private yamcs: YamcsService) {
    this.filteredOptions = this.formControl.valueChanges.pipe(
      tap((val) => this.onChange(val)),
      debounceTime(300),
      switchMap((val) => {
        if (val) {
          return this.queryParameters(val);
        } else {
          return Promise.resolve([]);
        }
      }),
    );
  }

  async queryParameters(q: string): Promise<Parameter[]> {
    const parameters = await this.yamcs.yamcsClient
      .getParameters(this.yamcs.instance!, {
        q,
        limit: 20,
        searchMembers: true,
      })
      .then((page) => page.parameters || []);

    // If there's only one suggestion, and it is equal to the user input,
    // than don't suggest anything at all.
    if (parameters.length === 1) {
      const memberPath = utils.getMemberPath(parameters[0]);
      if (memberPath === q) {
        return [];
      }
    }
    return parameters;
  }

  writeValue(value: any): void {
    this.formControl.setValue(value ?? null);
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {}
}
