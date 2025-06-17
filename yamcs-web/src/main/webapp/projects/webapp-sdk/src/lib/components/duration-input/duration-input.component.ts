import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  forwardRef,
  ViewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { YaIconAction } from '../icon-action/icon-action.component';

@Component({
  selector: 'ya-duration-input',
  templateUrl: './duration-input.component.html',
  styleUrl: './duration-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-duration-input',
  },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => YaDurationInput),
      multi: true,
    },
  ],
  imports: [YaIconAction],
})
export class YaDurationInput implements ControlValueAccessor {
  @ViewChild('input', { static: true })
  inputRef!: ElementRef<HTMLInputElement>;

  formattedValue = '00:00:00';
  disabled = false;

  // To distinguish if a focus event comes from keyboard navigation (tab),
  // or from a mouse click.
  mouseClicked = false;

  private onChange: (value: string | null) => void = () => {};

  writeValue(value: string | null): void {
    if (value && value.endsWith('s')) {
      const seconds = parseInt(value, 10);
      if (!isNaN(seconds)) {
        this.formattedValue = secondsToHHMMSS(seconds);
        this.onChange(`${seconds}s`);
      } else {
        this.formattedValue = '00:00:00';
        this.onChange('0s');
      }
    } else {
      this.formattedValue = '00:00:00';
      this.onChange('0s');
    }
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {}

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  onInput(event: Event): void {
    const input = this.inputRef.nativeElement;
    const value = input.value;
    const cursorPos = input.selectionStart || 0;
    const prevValue = this.formattedValue;

    if (this.isValidHHMMSS(value)) {
      const seconds = HHMMSStoSeconds(value);
      this.onChange(`${seconds}s`);
    } else {
      this.onChange(null);
    }
    this.formattedValue = value;

    // Auto-advance logic
    if (value.length >= 2 && prevValue.length < value.length) {
      if (cursorPos === 2 && value[2] === ':') {
        this.selectMinutes();
      } else if (cursorPos === 5 && value[5] === ':') {
        this.selectSeconds();
      }
    }
  }

  private selectHours() {
    if (this.isValidHHMMSS()) {
      setTimeout(() => {
        this.inputRef.nativeElement.setSelectionRange(0, 2);
      }, 0);
    }
  }

  private selectMinutes() {
    if (this.isValidHHMMSS()) {
      setTimeout(() => {
        this.inputRef.nativeElement.setSelectionRange(3, 5);
      }, 0);
    }
  }

  private selectSeconds() {
    if (this.isValidHHMMSS()) {
      setTimeout(() => {
        this.inputRef.nativeElement.setSelectionRange(6, 8);
      }, 0);
    }
  }

  onMouseDown(): void {
    this.mouseClicked = true;
  }

  onMouseUp(): void {
    this.mouseClicked = false;
  }

  onFocus(): void {
    if (!this.mouseClicked) {
      this.selectHours();
    }
  }

  onClick(event: MouseEvent): void {
    const input = this.inputRef.nativeElement;
    const cursorPos = input.selectionStart || 0;

    // Select hours, minutes, or seconds based on click position
    if (cursorPos >= 3 && cursorPos <= 5) {
      this.selectMinutes();
    } else if (cursorPos >= 6 && cursorPos <= 8) {
      this.selectSeconds();
    } else {
      this.selectHours();
    }
  }

  onKeyDown(event: KeyboardEvent): void {
    const input = this.inputRef.nativeElement;
    const cursorPos = input.selectionStart || 0;

    if (
      (event.key === 'Tab' && !event.shiftKey) ||
      event.key === 'ArrowRight'
    ) {
      // Move to next field
      if (cursorPos <= 2) {
        event.preventDefault();
        input.setSelectionRange(3, 5); // Move to minutes
      } else if (cursorPos >= 3 && cursorPos <= 5) {
        event.preventDefault();
        input.setSelectionRange(6, 8); // Move to seconds
      } else if (event.key === 'ArrowRight') {
        event.preventDefault();
      }
    } else if (
      (event.key === 'Tab' && event.shiftKey) ||
      event.key === 'ArrowLeft'
    ) {
      // Move to previous field
      if (cursorPos >= 6) {
        event.preventDefault();
        input.setSelectionRange(3, 5); // Move to minutes
      } else if (cursorPos >= 3 && cursorPos <= 5) {
        event.preventDefault();
        input.setSelectionRange(0, 2); // Move to hours
      } else if (event.key === 'ArrowLeft') {
        event.preventDefault();
      }
    } else if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
      event.preventDefault();
      if (!this.isValidHHMMSS()) {
        return;
      }

      const [hours, minutes, seconds] = this.formattedValue
        .split(':')
        .map(Number);
      let newHours = hours;
      let newMinutes = minutes;
      let newSeconds = seconds;

      if (cursorPos <= 2) {
        newHours = event.key === 'ArrowUp' ? hours + 1 : hours - 1;
        if (newHours > 99) newHours = 99;
        if (newHours < 0) newHours = 0;
      } else if (cursorPos >= 3 && cursorPos <= 5) {
        newMinutes = event.key === 'ArrowUp' ? minutes + 1 : minutes - 1;
        if (newMinutes > 59) newMinutes = 0;
        if (newMinutes < 0) newMinutes = 59;
      } else if (cursorPos >= 6) {
        newSeconds = event.key === 'ArrowUp' ? seconds + 1 : seconds - 1;
        if (newSeconds > 59) newSeconds = 0;
        if (newSeconds < 0) newSeconds = 59;
      }

      const newValue = `${pad(newHours)}:${pad(newMinutes)}:${pad(newSeconds)}`;
      this.formattedValue = newValue;
      const totalSeconds = HHMMSStoSeconds(newValue);
      this.onChange(`${totalSeconds}s`);

      // Restore selection range
      setTimeout(() => {
        if (cursorPos <= 2) {
          input.setSelectionRange(0, 2);
        } else if (cursorPos >= 3 && cursorPos <= 5) {
          input.setSelectionRange(3, 5);
        } else {
          input.setSelectionRange(6, 8);
        }
      }, 0);
    } else if (!isNumberKey(event)) {
      event.preventDefault();
    }
  }

  addHours(amount: number) {
    if (!this.isValidHHMMSS()) {
      return;
    }

    let [hours, minutes, seconds] = this.formattedValue.split(':').map(Number);
    hours += amount;
    if (hours > 99) hours = 99;
    if (hours < 0) hours = 0;

    const newValue = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
    this.formattedValue = newValue;
    const totalSeconds = HHMMSStoSeconds(newValue);
    this.onChange(`${totalSeconds}s`);
  }

  addMinutes(amount: number) {
    if (!this.isValidHHMMSS()) {
      return;
    }

    let [hours, minutes, seconds] = this.formattedValue.split(':').map(Number);
    minutes += amount;
    if (minutes > 59) minutes = 0;
    if (minutes < 0) minutes = 59;

    const newValue = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
    this.formattedValue = newValue;
    const totalSeconds = HHMMSStoSeconds(newValue);
    this.onChange(`${totalSeconds}s`);
  }

  addSeconds(amount: number) {
    if (!this.isValidHHMMSS()) {
      return;
    }

    let [hours, minutes, seconds] = this.formattedValue.split(':').map(Number);
    seconds += amount;
    if (seconds > 59) seconds = 0;
    if (seconds < 0) seconds = 59;

    const newValue = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
    this.formattedValue = newValue;
    const totalSeconds = HHMMSStoSeconds(newValue);
    this.onChange(`${totalSeconds}s`);
  }

  onBlur(): void {
    if (!this.isValidHHMMSS()) {
      this.formattedValue = '00:00:00';
      this.onChange('0s');
    }
  }

  private isValidHHMMSS(value?: string): boolean {
    const regex = /^[0-9][0-9]:[0-5][0-9]:[0-5][0-9]$/;
    return regex.test(value ?? this.formattedValue);
  }
}

function HHMMSStoSeconds(hhmmss: string): number {
  const [hours, minutes, seconds] = hhmmss.split(':').map(Number);
  return hours * 3600 + minutes * 60 + seconds;
}

function secondsToHHMMSS(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  return `${pad(hours)}:${pad(minutes)}:${pad(secs)}`;
}

function pad(num: number): string {
  return num.toString().padStart(2, '0');
}

function isNumberKey(event: KeyboardEvent) {
  return (
    /^[0-9]$/.test(event.key) ||
    (event.code.startsWith('Numpad') && /^[0-9]$/.test(event.key))
  );
}
