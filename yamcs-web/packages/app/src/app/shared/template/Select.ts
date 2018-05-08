import { Component, ChangeDetectionStrategy, EventEmitter, Input, Output, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

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

  public isSelected(id: string) {
    const selectedOption = this.selectedOption$.value;
    if (selectedOption !== null) {
      return selectedOption.id === id;
    }
    return false;
  }

  public select(id: string) {
    const option = this.findOption(id);
    if (option) {
      this.selectedOption$.next(option);
      this.change.emit(option.id);
    }
  }

  private findOption(id: string) {
    for (const option of this.options) {
      if (option.id === id) {
        return option;
      }
    }
    return null;
  }
}
