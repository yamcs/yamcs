import { ChangeDetectionStrategy, Component, forwardRef } from '@angular/core';
import { ControlValueAccessor, UntypedFormControl, NG_VALUE_ACCESSOR } from '@angular/forms';
import { BehaviorSubject, Observable } from 'rxjs';

@Component({
  selector: 'app-tag-select',
  templateUrl: './TagSelect.html',
  styleUrls: ['./TagSelect.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TagSelect),
      multi: true
    }
  ]
})
export class TagSelect implements ControlValueAccessor {

  control = new UntypedFormControl(null);

  filteredOptions: Observable<string[]>;

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
