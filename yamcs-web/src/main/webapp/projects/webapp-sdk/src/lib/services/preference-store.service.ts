import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface StoredColumnInfo {
  id: string;
  visible: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class PreferenceStore {

  // Keys without prefix
  preferences: { [key: string]: BehaviorSubject<any>; } = {};

  prefix = "yamcs.";

  constructor() {
    this.addPreference$("sidebar", true);
  }

  public addPreference$<Type>(key: string, defaultValue: Type): BehaviorSubject<Type> {
    this.preferences[key] = new BehaviorSubject<Type>(defaultValue);
    let item = localStorage.getItem(this.prefix + key);

    if (typeof defaultValue === 'boolean') {
      this.preferences[key].next(item === (!defaultValue).toString() ? !defaultValue : defaultValue);
    } else {
      this.preferences[key].next(item != null ? item : defaultValue);
    }
    return this.preferences[key];
  }

  public getPreference$(key: string): BehaviorSubject<any> {
    return this.preferences[key];
  }

  public getValue(key: string) {
    return this.preferences[key]?.getValue();
  }

  public setValue<Type>(key: string, value: Type) {
    this.preferences[key].next(value);
    localStorage.setItem(this.prefix + key, String(value));
  }

  /**
   * Returns a column preference from local storage.
   */
  public getStoredColumnInfo(source: string): StoredColumnInfo[] | undefined {
    const item = localStorage.getItem(`${this.prefix}${source}.cols`);
    if (item) {
      const cols: any[] = JSON.parse(item);
      for (let i = 0; i < cols.length; i++) {
        const col = cols[i];

        // In previous versions the type was a string[] of visible
        // columns. This has since been revised
        if (typeof col === 'string') {
          cols[i] = { id: col, visible: true };
        }
      }
      return cols;
    }
  }

  public setVisibleColumns(source: string, columns: StoredColumnInfo[]) {
    localStorage.setItem(`${this.prefix}${source}.cols`, JSON.stringify(columns));
  }
}
