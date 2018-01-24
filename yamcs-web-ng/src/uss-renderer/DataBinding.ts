export abstract class DataBinding {

  dynamicProperty: string;
  usingRaw: boolean;

  constructor(readonly type: string) {
  }
}
