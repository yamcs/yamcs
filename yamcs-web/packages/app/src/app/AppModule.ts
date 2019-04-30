import { APP_BASE_HREF } from '@angular/common';
import { APP_INITIALIZER, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AppRoutingModule } from './AppRoutingModule';
import { AppUtilModule } from './apputil/AppUtilModule';
import { AppComponent } from './apputil/pages/AppComponent';
import { APP_CONFIG } from './core/config/AppConfig';
import { ConfigService } from './core/services/ConfigService';
import { SharedModule } from './shared/SharedModule';

@NgModule({
  imports: [
    BrowserModule,
    BrowserAnimationsModule,

    AppRoutingModule, // Keep in front of modules that contribute child routing
    AppUtilModule,
    SharedModule,
  ],
  providers: [
    ConfigService,
    {
      provide: APP_BASE_HREF,
      useValue: '/',
    },
    {
      provide: APP_CONFIG,
      useValue: {},
    },
    {
      provide: APP_INITIALIZER,
      useFactory: (configService: ConfigService) => {
        return () => configService.loadWebsiteConfig();
      },
      multi: true,
      deps: [ ConfigService ]
    },
  ],
  exports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    SharedModule,
  ],
  bootstrap: [ AppComponent ]
})
export class AppModule {
}
