import { FullscreenOverlayContainer, OverlayContainer } from '@angular/cdk/overlay';
import { APP_BASE_HREF } from '@angular/common';
import { Provider } from '@angular/core';
import { DateAdapter } from '@angular/material/core';
import { MAT_TOOLTIP_DEFAULT_OPTIONS, MAT_TOOLTIP_DEFAULT_OPTIONS_FACTORY, MatTooltipDefaultOptions } from '@angular/material/tooltip';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { UtcDateAdapter } from './components/date-time-input/UtcDateAdapter';

const matTooltipOptions: MatTooltipDefaultOptions = {
  ...MAT_TOOLTIP_DEFAULT_OPTIONS_FACTORY(),
  disableTooltipInteractivity: true,
};

export function provideUtcNativeDateAdapter(): Provider[] {
  return [
    { provide: DateAdapter, useClass: UtcDateAdapter },
  ];
}

/**
 * base href can be a context path, and is set in index.html
 * so that it can be applied for loading static resources.
 * Here we derive APP_BASE_HREF from it.
 */
export function provideBaseHrefFromIndexHtml(): Provider[] {
  return [
    {
      provide: APP_BASE_HREF,
      useFactory: () => {
        const baseEl = document.getElementsByTagName('base')[0];
        return baseEl.getAttribute('href');
      },
    },
  ];
}

export function provideYamcsMaterialConfiguration(): Provider[] {
  return [
    provideUtcNativeDateAdapter(),
    { provide: MAT_TOOLTIP_DEFAULT_OPTIONS, useValue: matTooltipOptions },
    // The default OverlayContainer does not show overlays if
    // requestFullscreen is used.
    { provide: OverlayContainer, useClass: FullscreenOverlayContainer },
    provideNoopAnimations(),
  ];
}

export function provideYamcsWebExtension(): Provider[] {
  return [
    provideBaseHrefFromIndexHtml(),
    provideYamcsMaterialConfiguration(),
  ];
}
