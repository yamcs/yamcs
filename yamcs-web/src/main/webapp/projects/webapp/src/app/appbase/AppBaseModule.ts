import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AppComponent } from './pages/AppComponent';
import { ContextSwitchPage } from './pages/ContextSwitchPage';
import { CreateInstancePage1 } from './pages/CreateInstancePage1';
import { CreateInstancePage2 } from './pages/CreateInstancePage2';
import { CreateInstanceWizardStep } from './pages/CreateInstanceWizardStep';
import { ExtensionPage } from './pages/ExtensionPage';
import { ForbiddenPage } from './pages/ForbiddenPage';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';
import { Oops } from './pages/Oops';
import { ProfilePage } from './pages/ProfilePage';
import { ServerUnavailablePage } from './pages/ServerUnavailablePage';

const appComponents = [
  AppComponent,
  ContextSwitchPage,
  CreateInstancePage1,
  CreateInstancePage2,
  CreateInstanceWizardStep,
  ExtensionPage,
  ForbiddenPage,
  HomePage,
  NotFoundPage,
  Oops,
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
    appComponents,
  ],
  exports: [
    appComponents,
  ],
})
export class AppBaseModule {
}
