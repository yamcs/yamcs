import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, EventEmitter, forwardRef, Input, OnDestroy, Output, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { fromEvent, merge, Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';

@Component({
  selector: 'app-search-filter',
  templateUrl: './SearchFilter.html',
  styleUrls: ['./SearchFilter.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SearchFilter),
      multi: true,
    }
  ]
})
export class SearchFilter implements ControlValueAccessor, AfterViewInit, OnDestroy {

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

  ngAfterViewInit() {
    const keyObservable = fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(this.debounceTime),
      map(() => this.filter.nativeElement.value.trim()), // Detect 'distinct' on value not on KeyEvent
    );

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
    if (this.eventSubscription) {
      this.eventSubscription.unsubscribe();
    }
  }
}
