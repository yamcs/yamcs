import { APP_BASE_HREF } from '@angular/common';
import { APP_INITIALIZER, NgModule, isDevMode } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ServiceWorkerModule } from '@angular/service-worker';
import { ConfigService } from '@yamcs/webapp-sdk';
import * as dayjs from 'dayjs';
import 'dayjs/locale/en';
import * as utc from 'dayjs/plugin/utc';
import { AppRoutingModule } from './AppRoutingModule';
import { AppBaseModule } from './appbase/AppBaseModule';
import { AppComponent } from './appbase/pages/AppComponent';
import { SharedModule } from './shared/SharedModule';

dayjs.extend(utc);
dayjs.locale('en');

@NgModule({
  imports: [
    BrowserModule,
    BrowserAnimationsModule,

    AppBaseModule,
    AppRoutingModule, // Keep in front of modules that contribute child routing
    SharedModule,
    ServiceWorkerModule.register('ngsw-worker.js', {
      enabled: false && !isDevMode(),
      registrationStrategy: 'registerWithDelay:5000',
    }),
  ],
  providers: [
    ConfigService,
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
    }
  ],
  exports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    SharedModule,
  ],
  bootstrap: [AppComponent]
})
export class AppModule {
}
