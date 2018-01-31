import { Action } from '@ngrx/store';
import { Instance } from '../../../yamcs-client';

export const LOAD = '[Instances] Load';
export const LOAD_SUCCESS = '[Instances] Load Success';
export const LOAD_FAIL = '[Instances] Load Fail';
export const SELECT = '[Instances] Select';
export const UPDATE = '[Instances] Update';

export class LoadInstancesAction implements Action {
  readonly type = LOAD;
}

export class LoadInstancesSuccessAction implements Action {
  readonly type = LOAD_SUCCESS;

  constructor(readonly payload: Instance[]) {}
}

export class LoadInstancesFailAction implements Action {
  readonly type = LOAD_FAIL;

  constructor(readonly payload: any) {}
}

export class SelectInstanceAction implements Action {
  readonly type = SELECT;

  constructor(readonly payload: string) {}
}

export class UpdateInstanceAction implements Action {
  readonly type = UPDATE;

  constructor(readonly payload: Instance) {}
}
