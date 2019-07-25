import { APP_BASE_HREF } from '@angular/common';
import { APP_INITIALIZER, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AppRoutingModule } from './AppRoutingModule';
import { APP_CONFIG } from './core/config/AppConfig';
import { AppComponent } from './core/pages/AppComponent';
import { CreateInstancePage1 } from './core/pages/CreateInstancePage1';
import { CreateInstancePage2 } from './core/pages/CreateInstancePage2';
import { CreateInstanceWizardStep } from './core/pages/CreateInstanceWizardStep';
import { ForbiddenPage } from './core/pages/ForbiddenPage';
import { HomePage } from './core/pages/HomePage';
import { LoginPage } from './core/pages/LoginPage';
import { NotFoundPage } from './core/pages/NotFoundPage';
import { Oops } from './core/pages/Oops';
import { ProfilePage } from './core/pages/ProfilePage';
import { ServerUnavailablePage } from './core/pages/ServerUnavailablePage';
import { ConfigService } from './core/services/ConfigService';
import { SharedModule } from './shared/SharedModule';

const appComponents = [
  AppComponent,
  CreateInstancePage1,
  CreateInstancePage2,
  CreateInstanceWizardStep,
  ForbiddenPage,
  HomePage,
  LoginPage,
  NotFoundPage,
  Oops,
  ProfilePage,
  ServerUnavailablePage,
];

@NgModule({
  imports: [
    BrowserModule,
    BrowserAnimationsModule,

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
    }
  ],
  declarations: [
    appComponents,
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
