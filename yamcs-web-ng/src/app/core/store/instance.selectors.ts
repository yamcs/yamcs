import { createFeatureSelector, createSelector } from '@ngrx/store';
import { InstanceState, adapter } from './instance.reducers';


const selectInstanceState = createFeatureSelector<InstanceState>('instances');

const defaultSelectors = adapter.getSelectors(selectInstanceState);
export const selectInstanceIds = defaultSelectors.selectIds;
export const selectInstancesById = defaultSelectors.selectEntities;
export const selectTotalInstances = defaultSelectors.selectTotal;
export const selectInstances = defaultSelectors.selectAll;


export const selectCurrentInstanceId = createSelector(
  selectInstanceState,
  state => state.selectedInstanceId,
);

export const selectCurrentInstance = createSelector(
  selectInstancesById,
  selectCurrentInstanceId,
  (entities: any, selectedId: any) => {
    return selectedId && entities[selectedId];
  }
);
