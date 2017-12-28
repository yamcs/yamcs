import {
  ActionReducerMap,
  ActionReducer,
  MetaReducer,
} from '@ngrx/store';

import * as fromRouter from '@ngrx/router-store';

import { environment } from '../environments/environment';
import { RouterStateUrl } from './shared/utils';

/*
 * As mentioned, we treat each reducer like a table in a database. This means
 * our top level state interface is just a map of keys to inner state types.
 */
export interface State {
  routerReducer: fromRouter.RouterReducerState<RouterStateUrl>;
}

/*
 * Our state is composed of a map of action reducer functions.
 * These reducer functions are called with each dispatched action
 * and the current or initial state and return a new immutable state.
 */
export const reducers: ActionReducerMap<State> = {
  routerReducer: fromRouter.routerReducer,
};

export function logger(reducer: ActionReducer<State>): ActionReducer<State> {
  return function (state: State, action: any): State {
    console.log('action', action);
    const newState = reducer(state, action);
    console.log('state', newState);
    return newState;
  };
}

/*
 * By default, @ngrx/store uses combineReducers with the reducer map to compose
 * the root meta-reducer. To add more meta-reducers, provide an array of meta-reducers
 * that will be composed to form the root meta-reducer.
 */
export const metaReducers: Array<MetaReducer<State>> = !environment.production ? [logger] : [];
