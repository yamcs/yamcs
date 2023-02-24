import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

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
   *
   * To prevent broken or missing renamed columns, the preference
   * is reset as soon as a deprecated column is detected.
   */
  public getVisibleColumns(source: string, deprecated: string[] = []) {
    const item = localStorage.getItem(`${this.prefix}${source}.cols`);
    if (item) {
      const cols: string[] = JSON.parse(item);
      for (const col of cols) {
        for (const deprecatedCol of deprecated) {
          if (col === deprecatedCol) {
            localStorage.removeItem(`${this.prefix}${source}.cols`);
            return;
          }
        }
      }
      return cols;
    }
  }

  public setVisibleColumns(source: string, columns: string[]) {
    localStorage.setItem(`${this.prefix}${source}.cols`, JSON.stringify(columns));
  }

}
