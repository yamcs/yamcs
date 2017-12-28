import * as instancesActions from './instances.actions';

export interface State {
  selectedInstance: string | null;
}

export const initialState: State = {
  selectedInstance: null,
};

export function reducer(state = initialState, action: instancesActions.Actions): State {
  switch (action.type) {

    case instancesActions.SELECT:
      return { ...state, selectedInstance: action.payload };

    /*case instancesActions.UPDATE:
      // TODO use ngrx/entity instead
      if (state.selectedInstance && action.payload.name === state.selectedInstance.name) {
        return { ...state, selectedInstance: action.payload };
      } else {
        return state;
      }*/

    default:
      return state;
  }
}
