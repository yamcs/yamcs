import {
  DOCUMENT,
  DestroyRef,
  Injectable,
  effect,
  inject,
  signal,
} from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Preferences } from './preferences.service';

export type Theme = 'system' | 'light' | 'dark';

// TODO change to system, when dark mode is in decent shape
const DEFAULT_THEME: Theme = 'light';

@Injectable({ providedIn: 'root' })
export class AppearanceService {
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private prefs = inject(Preferences);

  public fullScreenRequested = signal(false);
  readonly themePreference = this.prefs.watchString(
    'appearance.theme',
    DEFAULT_THEME,
  );
  readonly dark = signal(false);

  public collapsed$ = new BehaviorSubject<boolean>(false);
  public fullScreenMode$ = new BehaviorSubject<boolean>(false);
  public focusMode$ = new BehaviorSubject<boolean>(false);
  public detailPane$ = new BehaviorSubject<boolean>(false);

  // Follow whether system prefers dark
  private mediaQuery: MediaQueryList;

  constructor() {
    this.mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    this.updateEffectiveDarkMode();

    this.listenToSystemColorPreference();
    this.listenToUserColorPreference();

    this.setupFullScreenChangeListener();
  }

  setTheme(theme: Theme): void {
    if (theme === 'light') {
      this.prefs.setString('appearance.theme', 'light');
    } else if (theme === 'dark') {
      this.prefs.setString('appearance.theme', 'dark');
    } else {
      this.prefs.setString('appearance.theme', 'system');
    }
  }

  private listenToSystemColorPreference() {
    const listener = () => {
      this.updateEffectiveDarkMode();
    };

    this.mediaQuery.addEventListener('change', listener);
    this.destroyRef.onDestroy(() => {
      this.mediaQuery.removeEventListener('change', listener);
    });
  }

  private listenToUserColorPreference() {
    effect(() => this.updateEffectiveDarkMode());
  }

  private updateEffectiveDarkMode() {
    const systemPrefersDark = this.mediaQuery.matches ?? false;
    const userPreference = this.themePreference();

    let dark = systemPrefersDark;
    if (userPreference === 'dark') {
      dark = true;
    } else if (userPreference === 'light') {
      dark = false;
    }

    this.dark.set(dark);
    if (dark) {
      this.document.documentElement.classList.add('dark');
    } else {
      this.document.documentElement.classList.remove('dark');
    }
  }

  private setupFullScreenChangeListener() {
    const listener = () => {
      if (this.document.fullscreenElement) {
        this.fullScreenMode$.next(true);
      } else {
        this.fullScreenRequested.set(false);
        this.fullScreenMode$.next(false);
        this.focusMode$.next(false);
      }
    };

    this.document.addEventListener('fullscreenchange', listener);
    this.destroyRef.onDestroy(() => {
      this.document.removeEventListener('fullscreenchange', listener);
    });

    effect(() => {
      if (!this.fullScreenRequested() && this.document.fullscreenElement) {
        this.document.exitFullscreen();
      }
    });
  }
}
