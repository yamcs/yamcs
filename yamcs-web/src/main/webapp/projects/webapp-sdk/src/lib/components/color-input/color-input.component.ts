import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  forwardRef,
  input,
  ViewChild,
} from '@angular/core';
import {
  ControlValueAccessor,
  FormControl,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
} from '@angular/forms';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { YaIconAction } from '../icon-action/icon-action.component';

@Component({
  selector: 'ya-color-input',
  templateUrl: './color-input.component.html',
  styleUrl: './color-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => YaColorInput),
      multi: true,
    },
  ],
  imports: [MatIcon, MatTooltip, ReactiveFormsModule, YaIconAction],
})
export class YaColorInput implements ControlValueAccessor {
  label = input('Set color');

  @ViewChild('colorInput')
  private colorInput: ElementRef<HTMLInputElement>;

  colorControl = new FormControl<string>('#000000');

  // Follows the HTML5 color input, but could also be null
  selectedColor: string | null = null;

  private onChange = (_: string | null) => {};

  constructor() {
    this.colorControl.valueChanges.subscribe((value) => {
      this.selectedColor = value;
      this.onChange(value);
    });
  }

  writeValue(obj: any): void {
    if (obj) {
      this.colorControl.setValue(obj);
    } else {
      this.selectedColor = null;
    }
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {}

  openColorPicker() {
    this.colorInput.nativeElement.click();
  }

  unsetColor() {
    this.selectedColor = null;
    this.onChange(null);
  }
}
