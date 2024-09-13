import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, forwardRef, input, OnDestroy, output, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { Completion } from '@codemirror/autocomplete';
import { BehaviorSubject, debounceTime, Subscription } from 'rxjs';
import { YaButton } from '../button/button.component';
import { YaFilterInput } from '../filter/filter-input.component';
import { YaFilterTextarea } from '../filter/filter-textarea.component';
import { FilterErrorMark } from '../filter/FilterErrorMark';

interface ErrorState {
  message: string;
  context?: FilterErrorMark;
}

@Component({
  standalone: true,
  selector: 'ya-search-filter2',
  templateUrl: './search-filter2.component.html',
  styleUrl: './search-filter2.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => YaSearchFilter2),
    multi: true,
  }],
  imports: [
    AsyncPipe,
    MatIcon,
    MatTooltip,
    ReactiveFormsModule,
    YaButton,
    YaFilterInput,
    YaFilterTextarea,
  ],
})
export class YaSearchFilter2 implements ControlValueAccessor, OnDestroy {

  placeholder = input<string>('Filter');
  width = input<string>('100%');
  debounceTime = input<number>(400);
  expanded = input<boolean>(false);
  completions = input<Completion[]>();

  /**
   * True if an unsubmitted filter is pending
   */
  dirty = signal<boolean>(false);

  /**
   * Latest typed value (including, before the user has pressed search).
   *
   * By default, debounced at 400ms.
   */
  typedValue = output<string>();

  /**
   * True if the current filter field is empty. This uses the
   * typed value, rather than the submitted value.
   */
  empty = signal<boolean>(false);

  /**
   * Actual current value (when search is pressed).
   *
   * Shouldn't need to be stored, but we do it so that we can
   * detect dirty flag.
   */
  private value: string;

  public onelineFilterInput = viewChild<YaFilterInput>('oneline');

  // Keep this a subject, not a signal.
  // When it's a signal, mysterious things happen with CM duplicating.
  errorState$ = new BehaviorSubject<ErrorState | null>(null);

  private onChange = (_: string | null) => { };

  // Form model, either managed with an HTML Input, or a CodeMirror editor.
  // This control updates instantly, as opposed to the exposed value.
  formControl = new FormControl<string | null>(null);

  private subscriptions: Subscription[] = [];

  constructor() {
    const formSubscription = this.formControl.valueChanges.subscribe(value => {
      this.empty.set(!!value);
      this.dirty.set((this.value || '') !== (value || ''));
    });
    this.subscriptions.push(formSubscription);

    const delayedFormSubscription = this.formControl.valueChanges.pipe(
      debounceTime(this.debounceTime()),
    ).subscribe(value => {
      this.typedValue.emit(value || '');
    });
    this.subscriptions.push(delayedFormSubscription);
  }

  /**
   * Returns the currently visible value (could be unsubmitted).
   */
  getTypedValue() {
    return this.formControl.value;
  }

  addErrorMark(message: string, context: FilterErrorMark) {
    this.errorState$.next({ message, context });
  }

  clearErrorMark() {
    this.errorState$.next(null);
  }

  getValue() {
    return this.formControl.value;
  }

  writeValue(value: any) {
    this.value = value ?? null;
    this.formControl.setValue(this.value);
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  doSearch() {
    if (this.errorState$.value) {
      return;
    }

    this.value = this.formControl.value ?? '';
    this.onChange(this.value);
    this.dirty.set(false);
  }

  ngOnDestroy() {
    this.subscriptions.forEach(s => s.unsubscribe());
  }
}
