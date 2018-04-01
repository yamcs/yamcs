import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

@Injectable()
export class PreferenceStore {

  darkMode$ = new BehaviorSubject<boolean>(false);

  constructor() {
    const darkMode = localStorage.getItem('yamcs.darkMode') === 'true';
    this.darkMode$.next(darkMode);
  }

  public isDarkMode() {
    return this.darkMode$.getValue();
  }

  public setDarkMode(darkMode: boolean) {
    this.darkMode$.next(darkMode);
    localStorage.setItem('yamcs.darkMode', String(darkMode));
  }
}
