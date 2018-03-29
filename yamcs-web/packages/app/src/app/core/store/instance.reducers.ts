import { createEntityAdapter, EntityAdapter, EntityState } from '@ngrx/entity';

import * as actions from './instance.actions';

import { Instance } from '@yamcs/client';

export interface InstanceState extends EntityState<Instance> {
  loading: boolean;
  loaded: boolean;
  selectedInstanceId: string | null;
}

export const adapter: EntityAdapter<Instance> = createEntityAdapter<Instance>({
  selectId: instance => instance.name,
  sortComparer: false,
});

export const initialState: InstanceState = adapter.getInitialState({
  loading: false,
  loaded: false,
  selectedInstanceId: null,
});

export function instanceReducer(state = initialState, action: any): InstanceState {
  switch (action.type) {

    case actions.LOAD:
      return {
        ...state,
        loading: true,
      };

    case actions.LOAD_SUCCESS:
      return {
        ...adapter.addAll(action.payload, state),
        loading: false,
        loaded: true,
        selectedInstanceId: state.selectedInstanceId,
      };

    case actions.LOAD_FAIL:
      return {
        ...state,
        loading: false,
      };

    case actions.SELECT:
      return {
        ...state,
        selectedInstanceId: action.payload,
      };

    case actions.UPDATE:
      return {
        ...adapter.addOne(action.payload, state),
        selectedInstanceId: state.selectedInstanceId,
      };

    default:
      return state;
  }
}
