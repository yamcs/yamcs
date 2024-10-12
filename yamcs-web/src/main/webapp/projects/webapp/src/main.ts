import { isDevMode, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding, withPreloading, withRouterConfig } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { provideBaseHrefFromIndexHtml, provideConfigInitializer, provideYamcsMaterialConfiguration } from '@yamcs/webapp-sdk';
import { provideSdkBridge } from '../../webapp-sdk/src/public-api';
import { CustomPreloadingStrategy } from './app/CustomPreloadingStrategy';
import { AppComponent } from './app/app.component';
import { APP_ROUTES } from './app/app.routes';
import { AgoPipe } from './app/shared/pipes/ago.pipe';

bootstrapApplication(AppComponent, {
  providers: [
    provideBaseHrefFromIndexHtml(),
    provideYamcsMaterialConfiguration(),
    provideConfigInitializer(),
    provideSdkBridge(),
    provideExperimentalZonelessChangeDetection(),
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
    AgoPipe,
  ],
}).catch(e => console.error(e));
