import { BreakpointObserver } from '@angular/cdk/layout';
import {
  DOCUMENT,
  DestroyRef,
  Injectable,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, map } from 'rxjs';
import { Preferences } from './preferences.service';

export type Theme = 'system' | 'light' | 'dark';

// TODO change to system, when dark mode is in decent shape
const DEFAULT_THEME: Theme = 'light';

@Injectable({ providedIn: 'root' })
export class AppearanceService {
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private prefs = inject(Preferences);
  private breakpointObserver = inject(BreakpointObserver);

  public fullScreenRequested = signal(false);
  readonly themePreference = this.prefs.watchString(
    'appearance.theme',
    DEFAULT_THEME,
  );
  readonly dark = signal(false);

  public fullScreenMode$ = new BehaviorSubject<boolean>(false);
  public focusMode$ = new BehaviorSubject<boolean>(false);
  public detailPane$ = new BehaviorSubject<boolean>(false);

  /**
   * Reactive signal representing whether the browser window is small
   */
  public smallWindow = toSignal(
    this.breakpointObserver
      .observe('(max-width: 768px)') // 768px is the exact width of the original iPad
      .pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  /**
   * Reactive signal representing whether the user prefers the left sidenav to be
   * collapsed or not.
   */
  private collapsedPreference = this.prefs.watchBoolean(
    'sidenav.collapsed',
    false,
  );

  /**
   * Reactive signal representing whether the left sidenav should be
   * collapsed or not.
   *
   * This is derived by considering:
   * - the browser width
   * - the collapsed/expanded user preference
   */
  isCollapsed = computed(() => {
    return this.smallWindow() || this.collapsedPreference();
  });

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
