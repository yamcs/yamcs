import { CountedValue } from './CountedValue';
import { canMerge, copyState, State } from './State';

export class StateRemapper {
  private mappings: Array<{ [key: string]: any }> = [];

  addMapping(mapping: { [key: string]: any }) {
    this.mappings.push(mapping);
  }

  applyMappings(states: State[]): State[] {
    // Note: a copy is done for each state, so as to keep the value
    // in the buffer immutable. This avoids issues with cyclic mapping
    // rules that otherwise may get applied over and over.
    const modifiedStates: State[] = [];
    for (const state of states) {
      const stateCopy = copyState(state);
      this.applyMappingsToState(stateCopy);
      modifiedStates.push(stateCopy);
    }
    this.reduceDistinctValues(modifiedStates);
    this.compactStates(modifiedStates);
    this.calculateMostFrequent(modifiedStates);
    return modifiedStates;
  }

  private applyMappingsToState(state: State) {
    const mappedValues: CountedValue[] = [];
    for (let inputValue of state.values) {
      let value = inputValue.value;
      let color: string | undefined = undefined;
      for (const mapping of this.mappings) {
        if (mapping.type === 'value') {
          if (value === mapping.value) {
            if (mapping.label) {
              value = mapping.label;
            }
            color = mapping.color || undefined;
          }
        } else if (mapping.type === 'range') {
          const start = Number(mapping.start);
          const end = Number(mapping.end);
          if (!isNaN(value as any)) {
            const numberValue = Number(value);
            if (numberValue >= start && numberValue <= end) {
              if (mapping.label) {
                value = mapping.label;
              }
              color = mapping.color || undefined;
            }
          }
        }
      }
      mappedValues.push({ value: value!, count: inputValue.count, color });
    }

    // If there were mappings applied, we may need to combine some values together
    const squashedValues: CountedValue[] = [];
    for (const mappedValue of mappedValues) {
      let prevMatch = squashedValues.find(
        (candidate) => candidate.value === mappedValue.value,
      );
      if (prevMatch) {
        prevMatch.count += mappedValue.count;
      } else {
        squashedValues.push(mappedValue);
      }
    }

    state.values = squashedValues;
  }

  private reduceDistinctValues(states: State[]) {
    // Identify the most frequent values among all states.
    // Reclassify non-frequent values as 'other', instead
    // of using up a color/legend entry.
    const maxValues = 10; // Keep below palette colors
    const countsByValue = new Map<any, number>();
    for (const state of states) {
      for (const countedValue of state.values) {
        const count = countsByValue.get(countedValue.value) || 0;
        countsByValue.set(countedValue.value, count + countedValue.count);
      }
    }

    const frequentValues = [...countsByValue.entries()]
      .sort((a, b) => {
        return b[1] - a[1]; // Descending by count
      })
      .slice(0, maxValues)
      .map((entry) => entry[0]);

    for (const state of states) {
      for (let i = state.values.length - 1; i >= 0; i--) {
        const countedValue = state.values[i];
        if (frequentValues.indexOf(countedValue.value) === -1) {
          state.values.splice(i, 1);
          state.otherCount += countedValue.count;
        }
      }
    }
  }

  /**
   * Merge consecutive states together, if they share the same set of values.
   */
  private compactStates(states: State[]) {
    let compacted: State[] = [];

    let prevState: State | null = null;
    for (const state of states) {
      if (prevState && canMerge(prevState, state)) {
        prevState.otherCount += state.otherCount;
        prevState.stop = state.stop;
        for (const v of state.values) {
          let prev = prevState.values.find((x) => x.value === v.value);
          if (prev) {
            prev.count += v.count;
          } else {
            prevState.values.push(v);
          }
        }
      } else {
        compacted.push(state);
        prevState = state;
      }
    }

    states.length = 0;
    states.push(...compacted);
  }

  private calculateMostFrequent(states: State[]) {
    for (const state of states) {
      let mostFrequent: CountedValue = { value: null, count: 0 };
      let distinctValues = 0;
      for (const valueCount of state.values) {
        if (valueCount.count > mostFrequent.count) {
          mostFrequent = { ...valueCount };
        }
        if (valueCount.count > 0) {
          distinctValues++;
        }
      }

      if (state.otherCount) {
        distinctValues++;
      }
      if (state.otherCount > mostFrequent.count) {
        mostFrequent = { value: '__OTHER', count: state.otherCount };
      }

      state.mostFrequentValue = mostFrequent;
      state.mixed = distinctValues > 1;
    }
  }
}
