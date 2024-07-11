import { AsyncPipe } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatIcon } from '@angular/material/icon';
import { Subject, Subscription, fromEvent, merge } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';

@Component({
  standalone: true,
  selector: 'ya-search-filter',
  templateUrl: './search-filter.component.html',
  styleUrl: './search-filter.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => YaSearchFilter),
    multi: true,
  }],
  imports: [
    AsyncPipe,
    MatIcon
],
})
export class YaSearchFilter implements ControlValueAccessor, AfterViewInit, OnDestroy {

  @ViewChild('input', { static: true })
  filter: ElementRef;

  @Input()
  placeholder = 'Filter';

  @Input()
  width = '400px';

  @Input()
  debounceTime = 400;

  @Input()
  icon = 'filter_list';

  @Output()
  onArrowDown = new EventEmitter<string>();

  @Output()
  onArrowUp = new EventEmitter<string>();

  @Output()
  onEnter = new EventEmitter<string>();

  showClear$ = new Subject<boolean>();

  private setEvent$ = new Subject<string>();
  private eventSubscription: Subscription;

  private onChange = (_: string | null) => { };

  constructor(private changeDetection: ChangeDetectorRef) {
  }

  ngAfterViewInit() {
    const keyObservable = fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(this.debounceTime),
      map(() => this.filter.nativeElement.value.trim()), // Detect 'distinct' on value not on KeyEvent
    );

    this.showClear$.next(!!this.getValue());
    this.changeDetection.detectChanges();

    this.eventSubscription = merge(keyObservable, this.setEvent$).pipe(
      distinctUntilChanged(),
    ).subscribe(value => {
      this.onChange(value);
      this.showClear$.next(!!value);
    });
  }

  getValue() {
    return this.filter.nativeElement.value.trim();
  }

  writeValue(value: any) {
    this.filter.nativeElement.value = value;
    this.setEvent$.next(value);
  }

  clearInput() {
    this.writeValue('');
    const el = this.filter.nativeElement as HTMLInputElement;
    el.focus();
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  onKeydown(event: KeyboardEvent) {
    switch (event.key) {
      case 'ArrowDown':
        this.onArrowDown.emit((event.target as HTMLInputElement).value);
        event.preventDefault();
        return false;
      case 'ArrowUp':
        this.onArrowUp.emit((event.target as HTMLInputElement).value);
        event.preventDefault();
        return false;
      case 'Enter':
        this.onEnter.emit((event.target as HTMLInputElement).value);
        event.preventDefault();
        return false;
    }
  }

  ngOnDestroy() {
    this.eventSubscription?.unsubscribe();
  }
}
