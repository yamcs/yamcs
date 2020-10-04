import { ChangeDetectionStrategy, Component, ElementRef, forwardRef, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { DateAdapter } from '@angular/material/core';
import { MatDatepicker } from '@angular/material/datepicker';
import * as utils from '../utils';
import { UtcDateAdapter } from './UtcDateAdapter';

// Used as a signal to show validation results
const INVALID_ISOSTRING = 'invalid';
const DAY_OF_YEAR_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

@Component({
  selector: 'app-date-time-input',
  templateUrl: './DateTimeInput.html',
  styleUrls: ['./DateTimeInput.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DateTimeInput),
      multi: true,
    }, {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => DateTimeInput),
      multi: true,
    }, {
      provide: DateAdapter,
      useClass: UtcDateAdapter,
    }
  ]
})
export class DateTimeInput implements ControlValueAccessor, Validator {

  @ViewChild('dayInput', { static: true })
  private dayInputComponent: ElementRef;

  @ViewChild('hourInput', { static: true })
  private hourInputComponent: ElementRef;

  @ViewChild('minuteInput', { static: true })
  private minuteInputComponent: ElementRef;

  @ViewChild('secondInput', { static: true })
  private secondInputComponent: ElementRef;

  @ViewChild('picker', { static: true })
  private picker: MatDatepicker<Date>;

  private onChange = (_: string | null) => { };

  // Called for initial values, assuming ISO strings
  writeValue(value: any) {
    if (value) {
      const iso = value as string;
      this.dayInputComponent.nativeElement.value = iso.substring(0, 10);
      this.picker.select(utils.toDate(value));
      const hours = iso.substring(11, 13);
      const minutes = iso.substring(14, 16);
      const seconds = iso.length >= 18 ? iso.substring(17, 19) : 0;
      this.hourInputComponent.nativeElement.value = hours;
      this.minuteInputComponent.nativeElement.value = minutes;
      this.secondInputComponent.nativeElement.value = seconds;
      this.fireChange();
    }
  }

  fireChange() {
    try {
      const dt = this.createDateOrThrow();
      this.onChange(dt?.toISOString() || null);
    } catch {
      // Trigger a validation error
      this.onChange(INVALID_ISOSTRING);
    }
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  validate(control: FormControl): ValidationErrors | null {
    if (control.value === INVALID_ISOSTRING) {
      return { date: 'Invalid date' };
    }
    return null;
  }

  // If the user is pasting an ISO string then apply it to all inputs
  processPaste(event: ClipboardEvent) {
    let data = (event.clipboardData || (window as any).clipboardData).getData('text');
    if (data) {
      try {
        const pastedDate = utils.toDate(data);
        if (pastedDate instanceof Date && !isNaN(pastedDate.getTime())) {
          event.preventDefault();
          this.writeValue(pastedDate.toISOString());
          return false;
        }
      } catch {
        // Ignore
      }
    }
  }

  private createDateOrThrow() {
    const dayInput = this.dayInputComponent.nativeElement.value;
    const hourInput = this.hourInputComponent.nativeElement.value;
    const minuteInput = this.minuteInputComponent.nativeElement.value;
    const secondInput = this.secondInputComponent.nativeElement.value;
    if (!dayInput && !hourInput && !minuteInput && !secondInput) {
      return null;
    }

    const match = dayInput.match(DAY_OF_YEAR_PATTERN);
    if (!match) {
      throw new Error('Invalid date pattern');
    }

    const year = Number(match[1]);
    const month = Number(match[2]) - 1;
    const day = Number(match[3]);

    const hours = hourInput ? Number(hourInput) : 0;
    const minutes = minuteInput ? Number(minuteInput) : 0;
    const seconds = secondInput ? Number(secondInput) : 0;

    return new Date(Date.UTC(year, month, day, hours, minutes, seconds));
  }
}
