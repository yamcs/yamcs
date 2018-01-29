export abstract class DataSourceBinding {

  dynamicProperty: string;
  usingRaw: boolean;

  constructor(readonly type: string) {
  }
}
