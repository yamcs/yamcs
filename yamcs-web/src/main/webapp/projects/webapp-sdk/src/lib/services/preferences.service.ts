import { Injectable, signal, Signal, WritableSignal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class Preferences {
  private booleanSignals = new Map<string, WritableSignal<boolean | null>>();
  private numberSignals = new Map<string, WritableSignal<number | null>>();
  private stringSignals = new Map<string, WritableSignal<string | null>>();

  private prefix = 'yamcs.';

  getBoolean(key: string): boolean | null;
  getBoolean(key: string, defaultValue: boolean): boolean;
  getBoolean(key: string, defaultValue?: boolean): boolean | null {
    const item = localStorage.getItem(`${this.prefix}${key}`);
    if (item === null) {
      return defaultValue ?? null;
    } else {
      return item === 'true';
    }
  }

  setBoolean(key: string, value: boolean | null) {
    if (value === null) {
      localStorage.removeItem(`${this.prefix}${key}`);
    } else {
      localStorage.setItem(`${this.prefix}${key}`, String(value));
    }

    const signal = this.booleanSignals.get(key);
    if (signal) {
      signal.set(value);
    }
  }

  watchBoolean(key: string): Signal<boolean | null> {
    const initialValue = this.getBoolean(key);
    let x = this.booleanSignals.get(key);
    if (!x) {
      x = signal(initialValue);
      this.booleanSignals.set(key, x);
    }
    return x.asReadonly();
  }

  getNumber(key: string): number | null;
  getNumber(key: string, defaultValue: number): number;
  getNumber(key: string, defaultValue?: number): number | null {
    const item = localStorage.getItem(`${this.prefix}${key}`);
    if (item === null) {
      return defaultValue ?? null;
    } else {
      return Number(item);
    }
  }

  setNumber(key: string, value: number | null) {
    if (value === null) {
      localStorage.removeItem(`${this.prefix}${key}`);
    } else {
      localStorage.setItem(`${this.prefix}${key}`, String(value));
    }

    const signal = this.numberSignals.get(key);
    if (signal) {
      signal.set(value);
    }
  }

  watchNumber(key: string): Signal<number | null> {
    const initialValue = this.getNumber(key);
    let x = this.numberSignals.get(key);
    if (!x) {
      x = signal(initialValue);
      this.numberSignals.set(key, x);
    }
    return x.asReadonly();
  }

  getString(key: string): string | null;
  getString(key: string, defaultValue: string): string;
  getString(key: string, defaultValue?: string): string | null {
    const item = localStorage.getItem(`${this.prefix}${key}`) ?? null;
    return item ?? defaultValue ?? null;
  }

  watchString(key: string): Signal<string | null> {
    const initialValue = this.getString(key);
    let x = this.stringSignals.get(key);
    if (!x) {
      x = signal(initialValue);
      this.stringSignals.set(key, x);
    }
    return x.asReadonly();
  }

  setString(key: string, value: string | null) {
    if (value === null) {
      localStorage.removeItem(`${this.prefix}${key}`);
    } else {
      localStorage.setItem(`${this.prefix}${key}`, value);
    }

    const signal = this.stringSignals.get(key);
    if (signal) {
      signal.set(value);
    }
  }
}
