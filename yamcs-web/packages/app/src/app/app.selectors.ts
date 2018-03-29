import { State } from './app.reducers';
import { createSelector } from '@ngrx/store';
import { RouterStateUrl } from './shared/routing';
import { RouterReducerState } from '@ngrx/router-store';

export const getRouterState = (state: State) => state.routerReducer;

export const getCurrentUrl = createSelector(getRouterState, (state: RouterReducerState<RouterStateUrl>) => state.state && state.state.url);
