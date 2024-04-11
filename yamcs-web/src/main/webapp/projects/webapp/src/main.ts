import { APP_BASE_HREF } from '@angular/common';
import { APP_INITIALIZER, isDevMode } from '@angular/core';
import { MAT_TOOLTIP_DEFAULT_OPTIONS, MAT_TOOLTIP_DEFAULT_OPTIONS_FACTORY, MatTooltipDefaultOptions } from '@angular/material/tooltip';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, withComponentInputBinding, withPreloading, withRouterConfig } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { ConfigService } from '@yamcs/webapp-sdk';
import { CustomPreloadingStrategy } from './app/CustomPreloadingStrategy';
import { AppComponent } from './app/app.component';
import { APP_ROUTES } from './app/app.routes';
import { AgoPipe } from './app/shared/pipes/ago.pipe';

export const matTooltipOptions: MatTooltipDefaultOptions = {
  ...MAT_TOOLTIP_DEFAULT_OPTIONS_FACTORY(),
  disableTooltipInteractivity: true,
};

bootstrapApplication(AppComponent, {
  providers: [
    ConfigService,
    { provide: MAT_TOOLTIP_DEFAULT_OPTIONS, useValue: matTooltipOptions },
    {
      provide: APP_BASE_HREF,
      useFactory: () => {
        // base href can be a context path, and is set in index.html
        // so that it can be applied for loading static resources.
        // Here we derive APP_BASE_HREF from it.
        const baseEl = document.getElementsByTagName('base')[0];
        return baseEl.getAttribute('href');
      },
    },
    {
      provide: APP_INITIALIZER,
      useFactory: (configService: ConfigService) => {
        return () => configService.loadWebsiteConfig();
      },
      multi: true,
      deps: [ConfigService]
    },
    provideNoopAnimations(),
    provideRouter(APP_ROUTES,
      withComponentInputBinding(),
      withPreloading(CustomPreloadingStrategy),
      withRouterConfig({
        onSameUrlNavigation: 'reload',
        paramsInheritanceStrategy: 'always',
      }),
    ),
    provideServiceWorker('ngsw-worker.js', {
      enabled: false && !isDevMode(),
      registrationStrategy: 'registerWithDelay:5000',
    }),
    [
      AgoPipe,
    ],
  ],
}).catch(e => console.error(e));
