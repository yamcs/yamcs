import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';

import { AppComponent } from './app.component';
import { SharedModule } from './shared/shared.module';
import { MdbModule } from './mdb/mdb.module';
import { AppRoutingModule } from './app-routing.module';
import { InstancesPageComponent } from './core/pages/instances.component';
import { InstancePageComponent } from './core/pages/instance.component';

@NgModule({
  declarations: [
    AppComponent,
    InstancesPageComponent,
    InstancePageComponent,
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,

    SharedModule,

    AppRoutingModule,
    MdbModule,
  ],
  providers: [],
  bootstrap: [ AppComponent ]
})
export class AppModule { }
