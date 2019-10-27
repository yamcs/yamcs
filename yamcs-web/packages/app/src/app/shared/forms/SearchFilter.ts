import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, forwardRef, Input, OnDestroy, ViewChild } from '@angular/core';
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

  ngOnDestroy() {
    if (this.eventSubscription) {
      this.eventSubscription.unsubscribe();
    }
  }
}
