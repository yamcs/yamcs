import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';

import { AppComponent } from './core/pages/AppComponent';
import { SharedModule } from './shared/SharedModule';
import { MdbModule } from './mdb/MdbModule';
import { AppRoutingModule } from './AppRoutingModule';

import { NotFoundPage } from './core/pages/NotFoundPage';
import { YamcsService } from './core/services/YamcsService';
import { APP_BASE_HREF } from '@angular/common';
import { HomePage } from './core/pages/HomePage';
import { ProfilePage } from './core/pages/ProfilePage';

import Dygraph from 'dygraphs';
import GridPlugin from './shared/widgets/GridPlugin';

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
    MdbModule,
  ],
  providers: [
    YamcsService,
    {
      provide: APP_BASE_HREF,
      useValue: '/',
    }
  ],
  bootstrap: [ AppComponent ]
})
export class AppModule {

  constructor() {
    // Install customized GridPlugin in global Dygraph object.
    Dygraph.Plugins['Grid'] = GridPlugin;
    Dygraph.PLUGINS = [
      Dygraph.Plugins['Legend'],
      Dygraph.Plugins['Axes'],
      Dygraph.Plugins['Annotations'],
      Dygraph.Plugins['ChartLabels'],
      Dygraph.Plugins['Grid'],
      Dygraph.Plugins['RangeSelector'],
    ];
  }
}
