import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class PreferenceStore {

  sidebar$ = new BehaviorSubject<boolean>(true);
  detailPane$ = new BehaviorSubject<boolean>(false);

  constructor() {
    const sidebar = !(localStorage.getItem('yamcs.sidebar') === 'false');
    this.sidebar$.next(sidebar);
    const detailPane = !(localStorage.getItem('yamcs.detailPane') === 'false');
    this.detailPane$.next(detailPane);
  }

  public showSidebar() {
    return this.sidebar$.getValue();
  }

  public showDetailPane() {
    return this.detailPane$.getValue();
  }

  public setShowSidebar(enabled: boolean) {
    this.sidebar$.next(enabled);
    localStorage.setItem('yamcs.sidebar', String(enabled));
  }

  public setShowDetailPane(enabled: boolean) {
    this.detailPane$.next(enabled);
    localStorage.setItem('yamcs.detailPane', String(enabled));
  }

  public setVisibleColumns(source: string, columns: string[]) {
    localStorage.setItem(`yamcs.${source}.cols`, JSON.stringify(columns));
  }

  /**
   * Returns a column preference from local storage.
   *
   * To prevent broken or missing renamed columns, the preference
   * is reset as soon as a deprecated column is detected.
   */
  public getVisibleColumns(source: string, deprecated: string[] = []) {
    const item = localStorage.getItem(`yamcs.${source}.cols`);
    if (item) {
      const cols: string[] = JSON.parse(item);
      for (const col of cols) {
        for (const deprecatedCol of deprecated) {
          if (col === deprecatedCol) {
            localStorage.removeItem(`yamcs.${source}.cols`);
            return;
          }
        }
      }
      return cols;
    }
  }
}
