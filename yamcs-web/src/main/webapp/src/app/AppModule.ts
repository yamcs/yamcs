import { APP_BASE_HREF } from '@angular/common';
import { APP_INITIALIZER, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import * as dayjs from 'dayjs';
import 'dayjs/locale/en';
import * as utc from 'dayjs/plugin/utc';
import { AppBaseModule } from './appbase/AppBaseModule';
import { AppComponent } from './appbase/pages/AppComponent';
import { AppRoutingModule } from './AppRoutingModule';
import { ConfigService } from './core/services/ConfigService';
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
  ],
  providers: [
    ConfigService,
    {
      provide: APP_BASE_HREF,
      useFactory: () => {
        // base href is set in index.html so that it can be applied
        // for loading static resources. Here we derive APP_BASE_HREF
        // from it (keep context path, but remove static path).
        // This is the value used by Angular Router where we don't
        // want to see the static prefix.
        const baseEl = document.getElementsByTagName('base')[0];
        return baseEl.getAttribute('href')?.replace('static/', '');
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
