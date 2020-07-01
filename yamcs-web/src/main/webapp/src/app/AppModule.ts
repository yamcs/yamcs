import { APP_BASE_HREF } from '@angular/common';
import { APP_INITIALIZER, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AppBaseModule } from './appbase/AppBaseModule';
import { AppComponent } from './appbase/pages/AppComponent';
import { AppRoutingModule } from './AppRoutingModule';
import { ConfigService } from './core/services/ConfigService';
import { SharedModule } from './shared/SharedModule';

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
        // for loading the initial set of js resources. We also copy
        // it here to make Angular aware of it and make it available
        // for injection.
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
