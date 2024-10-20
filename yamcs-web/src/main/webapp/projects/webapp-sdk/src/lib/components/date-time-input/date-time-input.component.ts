import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, forwardRef, Input, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, ValidationErrors, Validator } from '@angular/forms';
import { MatDatepicker, MatDatepickerInput } from '@angular/material/datepicker';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { formatInTimeZone } from 'date-fns-tz';
import { provideUtcNativeDateAdapter } from '../../providers';
import { Formatter } from '../../services/formatter.service';
import * as utils from '../../utils';
import { YaIconAction } from '../icon-action/icon-action.component';

export interface FireChangeOptions {
  /**
   * Update the HTML input element to show for example '07' instead of '7'
   */
  standardizeInputs?: boolean;
}

// Used as a signal to show validation results
const INVALID_ISOSTRING = 'invalid';
const DAY_OF_YEAR_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

@Component({
  standalone: true,
  selector: 'ya-date-time-input',
  templateUrl: './date-time-input.component.html',
  styleUrl: './date-time-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => YaDateTimeInput),
    multi: true,
  }, {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => YaDateTimeInput),
    multi: true,
  }, provideUtcNativeDateAdapter()],
  imports: [
    MatDatepicker,
    MatDatepickerInput,
    MatIcon,
    MatTooltip,
    YaIconAction,
  ],
})
export class YaDateTimeInput implements AfterViewInit, ControlValueAccessor, Validator {

  @Input()
  showMillis = false;

  @Input()
  showClear = false;

  @Input()
  showNow = false;

  @ViewChild('dayInput', { static: true })
  private dayInputComponent: ElementRef<HTMLInputElement>;

  @ViewChild('hourInput', { static: true })
  private hourInputComponent: ElementRef<HTMLInputElement>;

  @ViewChild('minuteInput', { static: true })
  private minuteInputComponent: ElementRef<HTMLInputElement>;

  @ViewChild('secondInput', { static: true })
  private secondInputComponent: ElementRef<HTMLInputElement>;

  @ViewChild('millisInput', { static: true })
  private millisInputComponent: ElementRef<HTMLInputElement>;

  @ViewChild('picker', { static: true })
  private picker: MatDatepicker<Date>;

  private onChange = (_: string | null) => { };

  constructor(private formatter: Formatter) {
  }

  ngAfterViewInit(): void {
    this.picker.closedStream.subscribe(() => {
      if (this.dayInputComponent.nativeElement.value) {
        this.hourInputComponent.nativeElement.focus();
        this.hourInputComponent.nativeElement.select();
      }
    });
  }

  // Called for initial values, assuming ISO strings
  writeValue(value: any) {
    if (value) {
      let iso = value as string;
      iso = formatInTimeZone(iso, this.formatter.getTimezone(), 'yyyy-MM-dd\'T\'HH:mm:ss.SSS');

      this.dayInputComponent.nativeElement.value = iso.substring(0, 10);
      this.picker.select(utils.toDate(iso));
      const hours = iso.substring(11, 13);
      const minutes = iso.substring(14, 16);
      const seconds = iso.length >= 18 ? iso.substring(17, 19) : '00';
      this.hourInputComponent.nativeElement.value = hours;
      this.minuteInputComponent.nativeElement.value = minutes;
      this.secondInputComponent.nativeElement.value = seconds;
      if (this.showMillis) {
        const millis = iso.length >= 22 ? iso.substring(20, 23) : '000';
        this.millisInputComponent.nativeElement.value = millis;
      }
    }
  }

  fireChange(options: FireChangeOptions = {}) {
    try {
      const dt = this.createDateOrThrow(options.standardizeInputs ?? true);
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

  validate(control: UntypedFormControl): ValidationErrors | null {
    if (control.value === INVALID_ISOSTRING) {
      return { date: true };
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
          this.fireChange();
          return false;
        }
      } catch {
        // Ignore
      }
    }
  }

  clearValue() {
    this.dayInputComponent.nativeElement.value = '';
    this.hourInputComponent.nativeElement.value = '';
    this.minuteInputComponent.nativeElement.value = '';
    this.secondInputComponent.nativeElement.value = '';
    this.millisInputComponent.nativeElement.value = '';
    this.fireChange();
  }

  setNow() {
    const now = new Date().toISOString();
    this.writeValue(now);
    this.fireChange();
  }

  private createDateOrThrow(standardizeInputs: boolean) {
    const dayInput = this.dayInputComponent.nativeElement.value;
    const hourInput = this.hourInputComponent.nativeElement.value;
    const minuteInput = this.minuteInputComponent.nativeElement.value;
    const secondInput = this.secondInputComponent.nativeElement.value;
    const millisInput = this.millisInputComponent.nativeElement.value;
    if (!dayInput) {
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
    if (!Number.isInteger(hours)) {
      throw new Error('Hours must be an integer');
    }

    const minutes = minuteInput ? Number(minuteInput) : 0;
    if (!Number.isInteger(minutes)) {
      throw new Error('Minutes must be an integer');
    }

    const seconds = secondInput ? Number(secondInput) : 0;
    if (!Number.isInteger(seconds)) {
      throw new Error('Seconds must be an integer');
    }

    const millis = millisInput ? Number(millisInput) : 0;
    if (!Number.isInteger(millis)) {
      throw new Error('Milliseconds must be an integer');
    }

    if (standardizeInputs) {
      const { nativeElement: hourEl } = this.hourInputComponent;
      if (hours < 10) {
        hourEl.value = '0' + hours;
      }

      const { nativeElement: minuteEl } = this.minuteInputComponent;
      if (minutes < 10) {
        minuteEl.value = '0' + minutes;
      }

      const { nativeElement: secondEl } = this.secondInputComponent;
      if (seconds < 10) {
        secondEl.value = '0' + seconds;
      }

      if (this.showMillis) {
        const { nativeElement: millisEl } = this.millisInputComponent;
        if (millis < 10) {
          millisEl.value = '00' + millis;
        } else if (millis < 100) {
          millisEl.value = '0' + millis;
        }
      }
    }

    return new Date(Date.UTC(year, month, day, hours, minutes, seconds, millis));
  }
}
