import { Component, ChangeDetectionStrategy, EventEmitter, Input, Output, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

export interface Option {
  id: string;
  label: string;
  selected?: boolean;
  group?: boolean;
}

@Component({
  selector: 'app-select',
  templateUrl: './Select.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Select implements OnChanges {

  @Input()
  options: Option[] = [];

  @Input()
  icon: string;

  @Output()
  change = new EventEmitter<string>();

  selectedOption$ = new BehaviorSubject<Option | null>(null);

  ngOnChanges() {
    for (const option of this.options) {
      if (option.selected) {
        this.selectedOption$.next(option);
      }
    }
  }

  isSelected(option: Option) {
    const selectedOption = this.selectedOption$.value;
    if (selectedOption !== null) {
      return selectedOption.id === option.id;
    }
    return false;
  }

  selectOption(option: Option) {
    this.selectedOption$.next(option);
    this.change.emit(option.id);
  }
}
