import { LayoutState } from '@yamcs/displays';

export type LayoutStorageState = { [key: string]: LayoutState };

export interface NamedLayout extends LayoutState {
  name: string;
}

export class LayoutStorage {

  static saveLayout(instance: string, name: string, layoutState: LayoutState) {
    const state = this.getLayoutState(instance);
    state[name] = layoutState;
    localStorage.setItem(`yamcs.${instance}.savedLayouts`, JSON.stringify(state));
    return this.getLayout(instance, name);
  }

  static deleteLayout(instance: string, name: string) {
    const state = this.getLayoutState(instance);
    delete state[name];
    localStorage.setItem(`yamcs.${instance}.savedLayouts`, JSON.stringify(state));
  }

  static getLayouts(instance: string): NamedLayout[] {
    const layouts = [];
    const state = this.getLayoutState(instance);
    for (const name of Object.keys(state)) {
      layouts.push({ ...state[name], name });
    }
    return layouts;
  }

  static getLayout(instance: string, name: string): NamedLayout {
    const state = this.getLayoutState(instance);
    return { ...state[name], name };
  }

  private static getLayoutState(instance: string) {
    const item = localStorage.getItem(`yamcs.${instance}.savedLayouts`);
    if (item) {
      const state = JSON.parse(item) as LayoutStorageState;
      return state;
    } else {
      return {};
    }
  }
}
