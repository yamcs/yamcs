import * as angularCommon from '@angular/common';
import * as angularCore from '@angular/core';
import { enableProdMode } from '@angular/core';
import * as angularForms from '@angular/forms';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { AppModule } from './app/AppModule';
import { environment } from './environments/environment';

// Hammer commented out because of memory leak which is
// not fixed in the release of Angular that we are using.
// https://github.com/angular/angular/pull/22156
//
// import 'hammerjs'; // Gesture support for some material components


// Register all supported plugin externals
SystemJS.set('@angular/core', SystemJS.newModule(angularCore));
SystemJS.set('@angular/common', SystemJS.newModule(angularCommon));
SystemJS.set('@angular/forms', SystemJS.newModule(angularForms));

if (environment.production) {
  enableProdMode();
}

platformBrowserDynamic().bootstrapModule(AppModule)
  .catch(err => console.log(err));
