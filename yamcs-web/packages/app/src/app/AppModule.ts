import { APP_BASE_HREF } from '@angular/common';
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AppRoutingModule } from './AppRoutingModule';
import { APP_CONFIG } from './core/config/AppConfig';
import { AppComponent } from './core/pages/AppComponent';
import { ForbiddenPage } from './core/pages/ForbiddenPage';
import { HomePage } from './core/pages/HomePage';
import { LoginPage } from './core/pages/LoginPage';
import { NotFoundPage } from './core/pages/NotFoundPage';
import { ProfilePage } from './core/pages/ProfilePage';
import { SharedModule } from './shared/SharedModule';



@NgModule({
  declarations: [
    AppComponent,
    ForbiddenPage,
    HomePage,
    LoginPage,
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
    {
      provide: APP_BASE_HREF,
      useValue: '/',
    },
    {
      provide: APP_CONFIG,
      useValue: {},
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
