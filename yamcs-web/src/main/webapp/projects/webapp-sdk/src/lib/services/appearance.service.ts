import {
  DOCUMENT,
  Inject,
  Injectable,
  OnDestroy,
  effect,
  signal,
} from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AppearanceService implements OnDestroy {
  public fullScreenRequested = signal(false);

  public fullScreenMode$ = new BehaviorSubject<boolean>(false);
  public focusMode$ = new BehaviorSubject<boolean>(false);
  public detailPane$ = new BehaviorSubject<boolean>(false);

  private fullScreenChangeListener = () => {
    if (this.document.fullscreenElement) {
      this.fullScreenMode$.next(true);
    } else {
      this.fullScreenRequested.set(false);
      this.fullScreenMode$.next(false);
      this.focusMode$.next(false);
    }
  };

  constructor(@Inject(DOCUMENT) private document: Document) {
    document.addEventListener(
      'fullscreenchange',
      this.fullScreenChangeListener,
    );

    effect(() => {
      if (!this.fullScreenRequested() && document.fullscreenElement) {
        document.exitFullscreen();
      }
    });
  }

  ngOnDestroy(): void {
    this.document.removeEventListener(
      'fullscreenchange',
      this.fullScreenChangeListener,
    );
  }
}
