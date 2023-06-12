import { SelectionModel } from '@angular/cdk/collections';
import { TrackByFunction } from '@angular/core';



/**
 * A SelectionModel that compares items via an arbitrary tracker function instead of
 * via the default reference equality.
 */
export class TrackBySelectionModel<T> extends SelectionModel<T> {

  constructor(private tracker: TrackByFunction<T>, multiple?: boolean, initiallySelectedValues?: T[]) {
    super(multiple, initiallySelectedValues);
  }

  /**
   * Inform this model of all new values. Previously selected values that are no longer
   * in the new collection get automatically deselected.
   */
  matchNewValues(newValues: T[]) {
    const newIds: any[] = [];
    for (const value of newValues) {
      newIds.push(this.tracker(-1, value));
    }

    const oldIds = this.selected.map(v => this.tracker(-1, v));

    const newSelected: T[] = [];
    for (const value of newValues) {
      const valueId = this.tracker(-1, value);
      if (oldIds.indexOf(valueId) !== -1) {
        newSelected.push(value);
      }
    }

    this.clear();
    if (newSelected.length) {
      this.select(...newSelected);
    }
  }


  override isSelected(value: T) {
    const valueId = this.tracker(-1, value);
    for (const candidate of this.selected) {
      if (valueId === this.tracker(-1, candidate)) {
        return true;
      }
    }
    return false;
  }
}
