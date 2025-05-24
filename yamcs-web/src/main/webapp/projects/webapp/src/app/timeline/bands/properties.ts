// Stores CSS colors mapped to their #RRGGBB hex equivalent
// For example, we also allow things like: "red", and "rgb(255, 0, 0)"
// in addition to #ff0000. But at HTML-level we require #ff0000
// (restriction of HTML color input type).
const colorCache: { [key: string]: string } = {};

export function convertColor(cssColor: string) {
  let hex = colorCache[cssColor];
  if (!hex) {
    const ctx = document.createElement('canvas').getContext('2d')!;
    ctx.fillStyle = cssColor;
    hex = String(ctx.fillStyle);
    colorCache[cssColor] = hex;
  }
  return hex;
}

export enum PropertyInputType {
  BOOLEAN,
  COLOR,
  NUMBER,
  SELECT,
  TEXT,
}

export interface PropertyInfo<T> {
  inputType: PropertyInputType;
  defaultValue: T | undefined;
}

export class BooleanProperty implements PropertyInfo<boolean> {
  inputType = PropertyInputType.BOOLEAN;
  defaultValue: boolean;
  constructor(defaultValue: boolean) {
    this.defaultValue = defaultValue;
  }
}

export class ColorProperty implements PropertyInfo<string> {
  inputType = PropertyInputType.COLOR;
  defaultValue: string;
  constructor(defaultValue: string) {
    this.defaultValue = defaultValue;
  }
}

export class NumberProperty implements PropertyInfo<number> {
  inputType = PropertyInputType.NUMBER;
  defaultValue: number | undefined;
  constructor(defaultValue?: number) {
    this.defaultValue = defaultValue;
  }
}

export class TextProperty implements PropertyInfo<string> {
  inputType = PropertyInputType.TEXT;
  defaultValue: string;
  constructor(defaultValue: string) {
    this.defaultValue = defaultValue;
  }
}

export class SelectProperty<T extends string> implements PropertyInfo<T> {
  inputType = PropertyInputType.SELECT;
  defaultValue: T;
  constructor(defaultValue: T) {
    this.defaultValue = defaultValue;
  }
}

export type PropertyInfoSet = { [key: string]: PropertyInfo<any> };

/**
 * Adds missing properties, and converts string values to type-specific values
 * depending on the provided info model.
 */
export function resolveProperties(
  info: PropertyInfoSet,
  properties: { [key: string]: string },
) {
  const defaultProperties: { [key: string]: any } = {};
  for (const p in info) {
    defaultProperties[p] = info[p].defaultValue;
  }
  return { ...defaultProperties, ...convertStringTypes(info, properties) };
}

export function removeUnsetProperties(properties: { [key: string]: any }) {
  for (const key in properties) {
    if (properties[key] === null || properties[key] === '') {
      delete properties[key];
    }
  }
}

function convertStringTypes(
  info: PropertyInfoSet,
  properties: { [key: string]: string },
) {
  const result: { [key: string]: any } = {};
  for (const key in properties) {
    const propertyInfo = info[key];
    if (!propertyInfo) {
      continue;
    }
    if (propertyInfo.inputType === PropertyInputType.BOOLEAN) {
      result[key] = properties[key] === 'true';
    } else if (propertyInfo.inputType === PropertyInputType.NUMBER) {
      result[key] = Number(properties[key]);
    } else if (propertyInfo.inputType === PropertyInputType.COLOR) {
      result[key] = convertColor(properties[key]);
    } else if (propertyInfo.inputType === PropertyInputType.TEXT) {
      result[key] = properties[key];
    } else if (propertyInfo.inputType === PropertyInputType.SELECT) {
      result[key] = properties[key];
    } else {
      console.warn(
        `Unexpected property input type for ${key}`,
        propertyInfo?.inputType,
      );
      result[key] = properties[key];
    }
  }
  return result;
}
