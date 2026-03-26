import {
  DestroyRef,
  inject,
  Injectable,
  signal,
  Signal,
  WritableSignal,
} from '@angular/core';

/**
 * Provides typed access to data stored in the browser's Local Storage.
 *
 * Either query the current value, or use one of the watch-methods to
 * get a signal for tracking changes.
 *
 * Preferences are always optional, however some overloaded signatures
 * exist to avoid doing null-checks if you can yourself provide a default
 * value.
 *
 * When using a signal you will also be notified of changes to the
 * local storage that occur in another window or tab.
 */
@Injectable({
  providedIn: 'root',
})
export class Preferences {
  private readonly destroyRef = inject(DestroyRef);

  // Map keys don't use the prefix
  private booleanSignals = new Map<string, WritableSignal<boolean | null>>();
  private numberSignals = new Map<string, WritableSignal<number | null>>();
  private stringSignals = new Map<string, WritableSignal<string | null>>();
  private objectSignals = new Map<string, WritableSignal<any | null>>();

  private prefix = 'yamcs.';

  constructor() {
    this.listenToLocalStorageChanges();
  }

  /**
   * Listen to preference changes coming from other windows/tabs
   */
  private listenToLocalStorageChanges() {
    const listener = (event: StorageEvent) => this.handleStorageEvent(event);
    window.addEventListener('storage', listener);
    this.destroyRef.onDestroy(() =>
      window.removeEventListener('storage', listener),
    );
  }

  getBoolean(key: string): boolean | null;
  getBoolean(key: string, defaultValue: boolean): boolean;
  getBoolean(key: string, defaultValue?: boolean): boolean | null;
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

  watchBoolean(key: string): Signal<boolean | null>;
  watchBoolean(key: string, defaultValue: boolean): Signal<boolean>;
  watchBoolean(key: string, defaultValue?: boolean): Signal<boolean | null>;
  watchBoolean(key: string, defaultValue?: boolean): Signal<boolean | null> {
    const initialValue = this.getBoolean(key) ?? defaultValue ?? null;
    let x = this.booleanSignals.get(key);
    if (!x) {
      x = signal(initialValue);
      this.booleanSignals.set(key, x);
    }
    return x.asReadonly();
  }

  getNumber(key: string): number | null;
  getNumber(key: string, defaultValue: number): number;
  getNumber(key: string, defaultValue?: number): number | null;
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

  watchNumber(key: string): Signal<number | null>;
  watchNumber(key: string, defaultValue: number): Signal<number>;
  watchNumber(key: string, defaultValue?: number): Signal<number | null>;
  watchNumber(key: string, defaultValue?: number): Signal<number | null> {
    const initialValue = this.getNumber(key) ?? defaultValue ?? null;
    let x = this.numberSignals.get(key);
    if (!x) {
      x = signal(initialValue);
      this.numberSignals.set(key, x);
    }
    return x.asReadonly();
  }

  getString(key: string): string | null;
  getString(key: string, defaultValue: string): string;
  getString(key: string, defaultValue?: string): string | null;
  getString(key: string, defaultValue?: string): string | null {
    const item = localStorage.getItem(`${this.prefix}${key}`) ?? null;
    return item ?? defaultValue ?? null;
  }

  watchString(key: string): Signal<string | null>;
  watchString(key: string, defaultValue: string): Signal<string>;
  watchString(key: string, defaultValue?: string): Signal<string | null>;
  watchString(key: string, defaultValue?: string): Signal<string | null> {
    const initialValue = this.getString(key) ?? defaultValue ?? null;
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

  getObject<T>(key: string): T | null;
  getObject<T>(key: string, defaultValue: T): T;
  getObject<T>(key: string, defaultValue?: T): T | null;
  getObject<T>(key: string, defaultValue?: T): T | null {
    const json = localStorage.getItem(`${this.prefix}${key}`);
    if (json) {
      const item = JSON.parse(json) as T;
      return item;
    } else {
      return defaultValue ?? null;
    }
  }

  watchObject<T>(key: string): Signal<T | null>;
  watchObject<T>(key: string, defaultValue: T): Signal<T>;
  watchObject<T>(key: string, defaultValue?: T): Signal<T | null>;
  watchObject<T>(key: string, defaultValue?: T): Signal<T | null> {
    const initialValue = this.getObject(key) ?? defaultValue ?? null;
    let x = this.objectSignals.get(key);
    if (!x) {
      x = signal(initialValue);
      this.objectSignals.set(key, x);
    }
    return x.asReadonly();
  }

  setObject<T>(key: string, value: T | null) {
    if (value === null) {
      localStorage.removeItem(`${this.prefix}${key}`);
    } else {
      const json = JSON.stringify(value);
      localStorage.setItem(`${this.prefix}${key}`, json);
    }
  }

  private handleStorageEvent(event: StorageEvent) {
    if (event.key?.startsWith(this.prefix)) {
      const key = event.key.substring(this.prefix.length);

      let booleanSignal = this.booleanSignals.get(key);
      if (booleanSignal) {
        booleanSignal.set(this.getBoolean(key));
      }

      let numberSignal = this.numberSignals.get(key);
      if (numberSignal) {
        numberSignal.set(this.getNumber(key));
      }

      let stringSignal = this.stringSignals.get(key);
      if (stringSignal) {
        stringSignal.set(this.getString(key));
      }

      let objectSignal = this.objectSignals.get(key);
      if (objectSignal) {
        objectSignal.set(this.getObject(key));
      }
    }
  }
}
