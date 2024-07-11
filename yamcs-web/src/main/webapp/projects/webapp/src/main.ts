import { APP_INITIALIZER, isDevMode, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding, withPreloading, withRouterConfig } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { ConfigService, provideBaseHrefFromIndexHtml, provideYamcsMaterialConfiguration } from '@yamcs/webapp-sdk';
import { CustomPreloadingStrategy } from './app/CustomPreloadingStrategy';
import { AppComponent } from './app/app.component';
import { APP_ROUTES } from './app/app.routes';
import { AgoPipe } from './app/shared/pipes/ago.pipe';

bootstrapApplication(AppComponent, {
  providers: [
    provideBaseHrefFromIndexHtml(),
    provideYamcsMaterialConfiguration(),
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
    ConfigService,
    {
      provide: APP_INITIALIZER,
      useFactory: (configService: ConfigService) => {
        return () => configService.loadWebsiteConfig();
      },
      multi: true,
      deps: [ConfigService]
    },
  ],
}).catch(e => console.error(e));
