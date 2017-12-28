import { Action } from '@ngrx/store';
import { Instance } from '../../../yamcs-client';

export const SELECT = '[Instances] Select';
export const UPDATE = '[Instances] Update';

export class SelectInstanceAction implements Action {
  readonly type = SELECT;

  constructor(public payload: string) {}
}

export class UpdateInstanceAction implements Action {
  readonly type = UPDATE;

  constructor(public payload: Instance) {}
}

/**
 * Export a type alias of all actions in this action group
 * so that reducers can easily compose action types
 */
export type Actions = SelectInstanceAction | UpdateInstanceAction;
