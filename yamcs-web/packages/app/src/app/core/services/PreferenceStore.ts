import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class PreferenceStore {

  darkMode$ = new BehaviorSubject<boolean>(false);
  detailPane$ = new BehaviorSubject<boolean>(false);

  constructor() {
    const darkMode = localStorage.getItem('yamcs.darkMode') === 'true';
    this.darkMode$.next(darkMode);
    const detailPane = localStorage.getItem('yamcs.detailPane') === 'true';
    this.detailPane$.next(detailPane);
  }

  public isDarkMode() {
    return this.darkMode$.getValue();
  }

  public showDetailPane() {
    return this.detailPane$.getValue();
  }

  public setDarkMode(darkMode: boolean) {
    this.darkMode$.next(darkMode);
    localStorage.setItem('yamcs.darkMode', String(darkMode));
  }

  public setShowDetailPane(enabled: boolean) {
    this.detailPane$.next(enabled);
    localStorage.setItem('yamcs.detailPane', String(enabled));
  }

  public setVisibleColumns(source: string, columns: string[]) {
    localStorage.setItem(`yamcs.${source}.cols`, JSON.stringify(columns));
  }

  public getVisibleColumns(source: string) {
    const item = localStorage.getItem(`yamcs.${source}.cols`);
    if (item) {
      return JSON.parse(item) as string[];
    } else {
      return [];
    }
  }
}
