import { ChangeDetectionStrategy, Component, forwardRef } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Parameter, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { debounceTime, map, Observable, switchMap, tap } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-parameter-input',
  templateUrl: './parameter-input.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => AppParameterInput),
    multi: true,
  }],
  imports: [
    WebappSdkModule,
  ],
})
export class AppParameterInput implements ControlValueAccessor {

  private onChange = (_: string | null) => { };

  formControl = new FormControl<string | null>('');
  filteredOptions: Observable<Parameter[]>;

  constructor(yamcs: YamcsService) {
    this.filteredOptions = this.formControl.valueChanges.pipe(
      tap(val => this.onChange(val)),
      debounceTime(300),
      switchMap(val => {
        if (val) {
          return yamcs.yamcsClient.getParameters(yamcs.instance!, {
            q: val,
            limit: 20,
            searchMembers: true,
          });
        } else {
          return Promise.resolve({ parameters: [] });
        }
      }),
      map(page => page.parameters || []),
    );
  }

  writeValue(value: any): void {
    this.formControl.setValue(value ?? null);
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
  }
}
