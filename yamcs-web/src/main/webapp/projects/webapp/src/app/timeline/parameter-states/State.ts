import { CountedValue } from './CountedValue';

export interface State {
  start: number;

  stop: number;

  // Preprocessed values (mappings applied)
  values: CountedValue[];

  // The most frequent value in a range
  mostFrequentValue: CountedValue;

  otherCount: number;

  // If true, different values fall within this range
  // (one of which may be 'Others')
  mixed: boolean;
}

export function copyState(state: State): State {
  const copiedValues: CountedValue[] = [];
  for (const value of state.values) {
    copiedValues.push({ ...value });
  }
  return {
    start: state.start,
    stop: state.stop,
    values: copiedValues,
    mostFrequentValue: { ...state.mostFrequentValue },
    otherCount: state.otherCount,
    mixed: state.mixed,
  };
}

export function canMerge(a: State, b: State): boolean {
  if (
    (a.otherCount === 0 && b.otherCount !== 0) ||
    (a.otherCount !== 0 && b.otherCount === 0)
  ) {
    return false;
  }
  if (a.stop !== b.start) {
    return false;
  }
  const aVals = a.values
    .filter((countedValue) => countedValue.count > 0)
    .map((countedValue) => countedValue.value)
    .sort();
  const bVals = b.values
    .filter((countedValue) => countedValue.count > 0)
    .map((countedValue) => countedValue.value)
    .sort();
  if (aVals.length !== bVals.length) {
    return false;
  }
  for (let i = 0; i < aVals.length; i++) {
    if (aVals[i] !== bVals[i]) {
      return false;
    }
  }
  return true;
}
