import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule, UntypedFormControl } from '@angular/forms';
import { MatIcon } from '@angular/material/icon';
import { BehaviorSubject } from 'rxjs';
import { YaButton } from '../button/button.component';
import { YaLabel } from '../label/label.component';

@Component({
  standalone: true,
  selector: 'ya-tag-select',
  templateUrl: './tag-select.component.html',
  styleUrl: './tag-select.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => YaTagSelect),
    multi: true
  }],
  imports: [
    AsyncPipe,
    MatIcon,
    ReactiveFormsModule,
    YaButton,
    YaLabel,
  ],
})
export class YaTagSelect implements ControlValueAccessor {

  control = new UntypedFormControl(null);

  private onChange = (_: string[]) => { };

  tags$ = new BehaviorSubject<string[]>([]);

  addTag() {
    const tag = this.control.value;
    if (tag) {
      const tags = [...this.tags$.value];
      if (tags.indexOf(tag) === -1) {
        tags.push(tag);
      }
      tags.sort();
      this.tags$.next(tags);
    }
    this.control.setValue('');
    this.onChange(this.tags$.value);
  }

  removeTag(tag: string) {
    const tags = [...this.tags$.value];
    const idx = tags.indexOf(tag);
    if (idx !== -1) {
      tags.splice(idx, 1);
    }
    this.tags$.next(tags);
    this.onChange(tags);
  }

  writeValue(obj: any) {
    if (obj) {
      const tags: string[] = obj;
      this.tags$.next(tags);
    } else {
      this.tags$.next([]);
    }
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }
}
