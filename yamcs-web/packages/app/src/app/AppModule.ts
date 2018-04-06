import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';
import { APP_BASE_HREF } from '@angular/common';

import { AppComponent } from './core/pages/AppComponent';
import { SharedModule } from './shared/SharedModule';
import { AppRoutingModule } from './AppRoutingModule';

import { NotFoundPage } from './core/pages/NotFoundPage';
import { ExtensionRegistry } from './core/services/ExtensionRegistry';
import { PreferenceStore } from './core/services/PreferenceStore';
import { YamcsService } from './core/services/YamcsService';
import { HomePage } from './core/pages/HomePage';
import { ProfilePage } from './core/pages/ProfilePage';

@NgModule({
  declarations: [
    AppComponent,
    HomePage,
    NotFoundPage,
    ProfilePage,
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,

    AppRoutingModule, // Keep in front of modules that contribute child routing
    SharedModule,
  ],
  providers: [
    ExtensionRegistry,
    PreferenceStore,
    YamcsService,
    {
      provide: APP_BASE_HREF,
      useValue: '/',
    }
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
