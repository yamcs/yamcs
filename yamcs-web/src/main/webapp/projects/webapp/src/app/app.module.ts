import { APP_BASE_HREF } from '@angular/common';
import { APP_INITIALIZER, NgModule, isDevMode } from '@angular/core';
import { MAT_TOOLTIP_DEFAULT_OPTIONS, MAT_TOOLTIP_DEFAULT_OPTIONS_FACTORY, MatTooltipDefaultOptions } from '@angular/material/tooltip';
import { BrowserModule } from '@angular/platform-browser';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ServiceWorkerModule } from '@angular/service-worker';
import { ConfigService } from '@yamcs/webapp-sdk';
import * as dayjs from 'dayjs';
import 'dayjs/locale/en';
import * as utc from 'dayjs/plugin/utc';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ContextSwitchComponent } from './appbase/context-switch/context-switch.component';
import { CreateInstancePage1Component } from './appbase/create-instance-page1/create-instance-page1.component';
import { CreateInstancePage2Component } from './appbase/create-instance-page2/create-instance-page2.component';
import { CreateInstanceWizardStepComponent } from './appbase/create-instance-wizard-step/create-instance-wizard-step.component';
import { ExtensionComponent } from './appbase/extension/extension.component';
import { ForbiddenComponent } from './appbase/forbidden/forbidden.component';
import { HomeComponent } from './appbase/home/home.component';
import { NotFoundComponent } from './appbase/not-found/not-found.component';
import { OopsComponent } from './appbase/oops/oops.component';
import { ProfileComponent } from './appbase/profile/profile.component';
import { ServerUnavailableComponent } from './appbase/server-unavailable/server-unavailable.component';
import { SharedModule } from './shared/SharedModule';

dayjs.extend(utc);
dayjs.locale('en');

export const matTooltipOptions: MatTooltipDefaultOptions = {
  ...MAT_TOOLTIP_DEFAULT_OPTIONS_FACTORY(),
  disableTooltipInteractivity: true,
};

@NgModule({
  imports: [
    BrowserModule,
    NoopAnimationsModule,

    ContextSwitchComponent,
    CreateInstancePage1Component,
    CreateInstancePage2Component,
    CreateInstanceWizardStepComponent,
    ExtensionComponent,
    ForbiddenComponent,
    HomeComponent,
    NotFoundComponent,
    OopsComponent,
    ProfileComponent,
    ServerUnavailableComponent,

    AppRoutingModule, // Keep in front of modules that contribute child routing
    SharedModule,
    ServiceWorkerModule.register('ngsw-worker.js', {
      enabled: false && !isDevMode(),
      registrationStrategy: 'registerWithDelay:5000',
    }),
  ],
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
    }
  ],
  declarations: [AppComponent],
  bootstrap: [AppComponent],
})
export class AppModule {
}
