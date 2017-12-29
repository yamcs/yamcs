import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';

import { StoreModule, Store } from '@ngrx/store';
import { EffectsModule } from '@ngrx/effects';
import { StoreRouterConnectingModule, RouterStateSerializer } from '@ngrx/router-store';

import { AppComponent } from './app.component';
import { SharedModule } from './shared/shared.module';
import { MdbModule } from './mdb/mdb.module';
import { AppRoutingModule } from './app-routing.module';
import { InstancesPageComponent } from './core/pages/instances.component';
import { InstancePageComponent } from './core/pages/instance.component';

import { CustomRouterStateSerializer } from './shared/utils';
import { reducers, metaReducers } from './app.reducers';

import { InstanceEffects } from './core/store/instance.effects';
import { LoadInstancesAction } from './core/store/instance.actions';
import { NotFoundPageComponent } from './core/pages/not-found.component';

@NgModule({
  declarations: [
    AppComponent,
    InstancesPageComponent,
    InstancePageComponent,
    NotFoundPageComponent,
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,

    AppRoutingModule, // Keep in front of modules that contribute child routing
    SharedModule,
    MdbModule,

    /**
     * StoreModule.forRoot is imported once in the root module, accepting a reducer
     * function or object map of reducer functions. If passed an object of
     * reducers, combineReducers will be run creating your application
     * meta-reducer. This returns all providers for an @ngrx/store
     * based application.
     */
    StoreModule.forRoot(reducers, { metaReducers }),

    /**
     * @ngrx/router-store keeps router state up-to-date in the store.
     */
    StoreRouterConnectingModule,

    /**
     * EffectsModule.forRoot() is imported once in the root module and
     * sets up the effects class to be initialized immediately when the
     * application starts.
     *
     * See: https://github.com/ngrx/platform/blob/master/docs/effects/api.md#forroot
     */
    EffectsModule.forRoot([
      InstanceEffects,
    ]),
  ],
  providers: [
    /**
     * The `RouterStateSnapshot` provided by the `Router` is a large complex structure.
     * A custom RouterStateSerializer is used to parse the `RouterStateSnapshot` provided
     * by `@ngrx/router-store` to include only the desired pieces of the snapshot.
     */
    { provide: RouterStateSerializer, useClass: CustomRouterStateSerializer },
  ],
  bootstrap: [ AppComponent ]
})
export class AppModule {

  constructor(store: Store<any>) {
    store.dispatch(new LoadInstancesAction());
  }
}
