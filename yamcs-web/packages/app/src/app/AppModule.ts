import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';

import { StoreModule, Store } from '@ngrx/store';
import { EffectsModule } from '@ngrx/effects';
import { StoreRouterConnectingModule, RouterStateSerializer } from '@ngrx/router-store';

import { AppComponent } from './core/pages/AppComponent';
import { SharedModule } from './shared/SharedModule';
import { MdbModule } from './mdb/MdbModule';
import { AppRoutingModule } from './AppRoutingModule';

import { RouterStateSnapshot } from '@angular/router';
import { reducers, metaReducers } from './app.reducers';

import { InstanceEffects } from './core/store/instance.effects';
import { LoadInstancesAction } from './core/store/instance.actions';
import { NotFoundPage } from './core/pages/NotFoundPage';
import { RouterStateUrl } from './shared/routing';
import { YamcsService } from './core/services/YamcsService';
import { APP_BASE_HREF } from '@angular/common';
import { HomePage } from './core/pages/HomePage';
import { ProfilePage } from './core/pages/ProfilePage';

import Dygraph from 'dygraphs';
import GridPlugin from './shared/widgets/GridPlugin';

/**
 * The RouterStateSerializer takes the current RouterStateSnapshot
 * and returns any pertinent information needed. The snapshot contains
 * all information about the state of the router at the given point in time.
 * The entire snapshot is complex and not always needed. In this case, you only
 * need the URL and query parameters from the snapshot in the store. Other items could be
 * returned such as route parameters and static route data.
 */
export class CustomRouterStateSerializer implements RouterStateSerializer<RouterStateUrl> {
  serialize(routerState: RouterStateSnapshot): RouterStateUrl {
    return {
      url: routerState.url,
      queryParams: routerState.root.queryParams,
    };
  }
}

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
    YamcsService,
    {
      provide: APP_BASE_HREF,
      useValue: '/',
    },
    {
      provide: RouterStateSerializer,
      useClass: CustomRouterStateSerializer,
    }
  ],
  bootstrap: [ AppComponent ]
})
export class AppModule {

  constructor(store: Store<any>) {
    store.dispatch(new LoadInstancesAction());

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
