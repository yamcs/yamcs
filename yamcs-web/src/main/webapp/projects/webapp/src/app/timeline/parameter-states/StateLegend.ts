import { ColorMap } from './ColorMap';
import { State } from './State';

export class StateLegend {
  private colorByValue = new Map<string, string>();
  private colorMap = new ColorMap();

  entries() {
    return this.colorByValue.entries();
  }

  /**
   * Forget all color assignments. Do this after changing
   * the viewport, to avoid colors getting reused.
   */
  resetColorAssignment() {
    this.colorMap.reset();
  }

  recalculate(states: State[]) {
    const legendMap = new Map<string, string | undefined>();
    for (const state of states) {
      for (const stateValue of state.values) {
        if (!legendMap.has(stateValue.value!)) {
          legendMap.set(stateValue.value!, stateValue.color);
        }
      }
    }
    const sorted = [...legendMap.entries()].sort((a, b) => {
      return a[0].localeCompare(b[0], undefined, {
        numeric: true,
        sensitivity: 'base',
      });
    });
    this.colorByValue.clear();
    for (const [value, color] of sorted) {
      if (color) {
        this.colorByValue.set(value, color);
      } else {
        // For better predictability, apply autocolors only after having
        // sorted the values.
        const autoColor = this.colorMap.colorForValue(value);
        this.colorByValue.set(value, autoColor);
      }
    }
    this.colorByValue.set('__OTHER', this.colorMap.colorForValue('__OTHER'));
  }

  getBackground(state: State) {
    return (
      state.mostFrequentValue.color ??
      this.colorMap.colorForValue(state.mostFrequentValue.value)
    );
  }

  getForeground(state: State) {
    const background = this.getBackground(state);
    return this.isDark(background) ? '#fff' : '#000';
  }

  getLabel(state: State) {
    return state.mostFrequentValue.value ?? 'null';
  }

  private isDark(hexColor: string) {
    const color =
      hexColor.charAt(0) === '#' ? hexColor.substring(1, 7) : hexColor;
    const r = parseInt(color.substring(0, 2), 16);
    const g = parseInt(color.substring(2, 4), 16);
    const b = parseInt(color.substring(4, 6), 16);
    return r * 0.299 + g * 0.587 + b * 0.114 <= 186;
  }
}
