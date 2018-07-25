import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AppComponent } from './pages/AppComponent';
import { ForbiddenPage } from './pages/ForbiddenPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ProfilePage } from './pages/ProfilePage';
import { ServerUnavailablePage } from './pages/ServerUnavailablePage';

const apputilComponents = [
  AppComponent,
  ForbiddenPage,
  HomePage,
  LoginPage,
  NotFoundPage,
  ProfilePage,
  ServerUnavailablePage,
];

/**
 * Module exporting reusable app-level components.
 * These cannot be in AppModule, because AppModule
 * is not exported in the ng-packagr and ng-packagr
 * complains about components that are not in a
 * module.
 */
@NgModule({
  imports: [
    SharedModule,
  ],
  declarations: [
    apputilComponents,
  ],
  exports: [
    apputilComponents,
  ],
})
export class AppUtilModule {
}
