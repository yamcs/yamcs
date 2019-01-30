export interface Action {
  type: string;
}

export interface OpenDisplayAction extends Action {
  path: string;
  mode: number;
}

export interface ExecuteJavaScriptAction extends Action {
  text?: string;
}
