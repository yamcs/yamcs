import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AppComponent } from './pages/AppComponent';
import { CreateInstancePage1 } from './pages/CreateInstancePage1';
import { CreateInstancePage2 } from './pages/CreateInstancePage2';
import { CreateInstanceWizardStep } from './pages/CreateInstanceWizardStep';
import { ForbiddenPage } from './pages/ForbiddenPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ProfilePage } from './pages/ProfilePage';
import { ServerUnavailablePage } from './pages/ServerUnavailablePage';

const apputilComponents = [
  AppComponent,
  CreateInstancePage1,
  CreateInstancePage2,
  CreateInstanceWizardStep,
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
