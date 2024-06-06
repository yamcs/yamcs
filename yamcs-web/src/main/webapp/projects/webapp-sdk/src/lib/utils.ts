import { Instance, Parameter, ParameterMember, ParameterType, UnitInfo, Value } from './client';

/**
 * Minimum valid date for a proto Timestamp
 */
export const MIN_DATE = new Date("0001-01-01T00:00:00Z");

/**
 * Maximum valid date for a proto Timestamp
 */
export const MAX_DATE = new Date("9999-12-31T23:59:59.999999999Z");

/**
 * Return a new date, clamped to the range {@link MIN_DATE} - {@link MAX_DATE}
 */
export function clampDate(date: Date | number | string): Date {
  const copy = new Date(date);
  if (copy.getTime() < MIN_DATE.getTime()) {
    copy.setTime(MIN_DATE.getTime());
  } else if (copy.getTime() > MAX_DATE.getTime()) {
    copy.setTime(MAX_DATE.getTime());
  }
  return copy;
}

/**
 * Deep clones an object.
 * https://github.com/whatwg/html/issues/793
 */
export function structuredClone(obj: {}) {
  return new Promise(resolve => {
    const { port1, port2 } = new MessageChannel();
    port2.onmessage = ev => resolve(ev.data);
    port1.postMessage(obj);
  });
}

/**
 * Substracts an ISO 8601 duration string from the given date.
 * Fractions are not currently supported.
 */
export function subtractDuration(date: Date, isoDuration: string) {
  const regex = /P((([0-9]*\.?[0-9]*)Y)?(([0-9]*\.?[0-9]*)M)?(([0-9]*\.?[0-9]*)W)?(([0-9]*\.?[0-9]*)D)?)?(T(([0-9]*\.?[0-9]*)H)?(([0-9]*\.?[0-9]*)M)?(([0-9]*\.?[0-9]*)S)?)?/;

  const matchResult = isoDuration.match(regex);
  if (!matchResult) {
    throw new Error(`Invalid ISO 8601 duration: ${isoDuration}`);
  }

  const dt = new Date(date.getTime());
  if (matchResult[3]) { // e.g. P1Y
    dt.setUTCFullYear(date.getUTCFullYear() - parseFloat(matchResult[3]));
  }
  if (matchResult[5]) { // e.g. P1M
    dt.setUTCMonth(date.getUTCMonth() - parseFloat(matchResult[5]));
  }
  if (matchResult[7]) { // e.g. P1W
    dt.setUTCDate(date.getUTCDate() - (7 * parseFloat(matchResult[7])));
  }
  if (matchResult[9]) { // e.g. P1D
    dt.setUTCDate(date.getUTCDate() - parseFloat(matchResult[9]));
  }
  if (matchResult[12]) { // e.g. PT1H
    dt.setUTCHours(date.getUTCHours() - parseFloat(matchResult[12]));
  }
  if (matchResult[14]) { // e.g. PT1M
    dt.setUTCMinutes(date.getUTCMinutes() - parseFloat(matchResult[14]));
  }
  if (matchResult[16]) { // e.g. PT1S
    dt.setUTCSeconds(date.getUTCSeconds() - parseFloat(matchResult[16]));
  }
  return dt;
}

export function convertProtoDurationToMillis(protoDuration: string) {
  if (!protoDuration.endsWith("s")) {
    throw new Error(`Invalid proto duration: ${protoDuration}`);
  }
  const parts = protoDuration.substring(0, protoDuration.length - 1).split(".", 2);
  if (parts.length === 1) {
    return Number(parts[0]) * 1000;
  } else {
    const seconds = Number(parts[0]);
    let millisString = parts[1].substr(0, 3);
    let millis = Number(millisString);
    if (millisString.length === 1) {
      millis *= 100;
    } else if (millisString.length === 2) {
      millis *= 10;
    }
    return (seconds * 1000) + millis;
  }
}

/**
 * Converts an ISO duration string to the equivalent number of milliseconds.
 * This only works with seconds, minutes, hours and days.
 */
export function convertDurationToMillis(isoDuration: string) {
  const regex = /P((([0-9]*\.?[0-9]*)Y)?(([0-9]*\.?[0-9]*)M)?(([0-9]*\.?[0-9]*)W)?(([0-9]*\.?[0-9]*)D)?)?(T(([0-9]*\.?[0-9]*)H)?(([0-9]*\.?[0-9]*)M)?(([0-9]*\.?[0-9]*)S)?)?/;

  const matchResult = isoDuration.match(regex);
  if (!matchResult) {
    throw new Error(`Invalid ISO 8601 duration: ${isoDuration}`);
  }

  let millis = 0;
  if (matchResult[9]) { // e.g. P1D
    millis += parseFloat(matchResult[9]) * 86400000;
  }
  if (matchResult[12]) { // e.g. PT1H
    millis += parseFloat(matchResult[12]) * 3600000;
  }
  if (matchResult[14]) { // e.g. PT1M
    millis += parseFloat(matchResult[14]) * 60000;
  }
  if (matchResult[16]) { // e.g. PT1S
    millis += parseFloat(matchResult[16]) * 1000;
  }
  return millis;
}

export function convertValueToNumber(value: Value) {
  switch (value.type) {
    case 'FLOAT':
      return value.floatValue!;
    case 'DOUBLE':
      return value.doubleValue!;
    case 'UINT32':
      return value.uint32Value!;
    case 'SINT32':
      return value.sint32Value!;
    case 'UINT64':
      return value.uint64Value!;
    case 'SINT64':
      return value.sint64Value!;
    default:
      return null; // Assuming not a number
  }
}

export function convertBase64ToHex(base64: string) {
  const raw = window.atob(base64);
  let result = '';
  for (let i = 0; i < raw.length; i++) {
    const hex = raw.charCodeAt(i).toString(16);
    result += (hex.length === 2 ? hex : '0' + hex);
  }
  return result;
}

export function convertHexToBase64(hex: string) {
  if (hex.length % 2) {
    hex = '0' + hex;
  }
  const barr = [];
  for (let i = 0; i < hex.length - 1; i += 2) {
    barr.push(parseInt(hex.substr(i, 2), 16));
  }
  const str = String.fromCharCode.apply(String, barr);
  return window.btoa(str);
}

export function toValue(value: any): Value {
  if (Array.isArray(value)) {
    const arrayValue: Value[] = [];
    for (const item of value) {
      arrayValue.push(toValue(item));
    }
    return { type: 'ARRAY', arrayValue };
  } else if (typeof value === 'object') {
    const names = [];
    const values = [];
    for (const name in value) {
      names.push(name);
      values.push(toValue(value[name]));
    }
    return { type: 'AGGREGATE', aggregateValue: { name: names, value: values } };
  } else if (value === true || value === false) {
    return { type: 'BOOLEAN', booleanValue: value };
  } else {
    return { type: 'STRING', stringValue: String(value) };
  }
}

export function convertValue(value: Value) {
  switch (value.type) {
    case 'FLOAT':
      return value.floatValue;
    case 'DOUBLE':
      return value.doubleValue;
    case 'UINT32':
      return value.uint32Value;
    case 'SINT32':
      return value.sint32Value;
    case 'UINT64':
      return value.uint64Value;
    case 'SINT64':
      return value.sint64Value;
    case 'BOOLEAN':
      return value.booleanValue;
    case 'TIMESTAMP':
      return toDate(value.stringValue!);
    case 'BINARY':
      return window.atob(value.binaryValue!);
    case 'ENUMERATED':
    case 'STRING':
      return value.stringValue;
    case 'ARRAY':
      const arrayValue: any[] = [];
      for (const item of (value.arrayValue || [])) {
        arrayValue.push(convertValue(item));
      }
      return arrayValue;
    case 'AGGREGATE':
      const aggregate: Map<string, any> = new Map<string, any>();
      let membersLength = value.aggregateValue?.value.length || 0;
      for (let i = 0; i < membersLength; i++) {
        let memberName = value.aggregateValue?.name[i] || "";
        let memberValue = convertValue(value.aggregateValue?.value[i] as Value);
        aggregate.set(memberName, memberValue);
      }
      return aggregate;
    default:
      throw new Error(`Unexpected value type ${value.type}`);
  }
}

const adjectives = [
  'amused', 'acid', 'adaptable', 'alleged', 'agreeable', 'aspiring', 'awestruck', 'berserk', 'bright',
  'busy', 'calm', 'caring', 'chilly', 'cool', 'curious', 'dapper', 'dazzling', 'dizzy', 'eager',
  'elite', 'energetic', 'familiar', 'famous', 'fancy', 'fast', 'festive', 'flawless', 'fresh',
  'friendly', 'funny', 'furry', 'gifted', 'groovy', 'helpful', 'hungry', 'jolly', 'jumpy', 'lucky',
  'polite', 'quick', 'quiet', 'rapid', 'rare', 'scary', 'surprised', 'swift', 'tall', 'tame', 'thin',
  'tidy', 'tiny', 'thirsty', 'tough', 'wacky', 'wild'];

const animals = [
  'alligator', 'ant', 'anteater', 'antelope', 'armadillo', 'badger', 'bat', 'bear', 'bee',
  'beetle', 'buffalo', 'butterfly', 'camel', 'cat', 'chameleon', 'cheetah', 'chicken',
  'cicada', 'chimp', 'clam', 'cow', 'coyote', 'crab', 'cricket', 'crow', 'deer', 'dog', 'dolphin',
  'donkey', 'dove', 'dragonfly', 'duck', 'eagle', 'eel', 'elephant', 'ferret', 'fish', 'fly', 'fox',
  'frog', 'gazelle', 'goat', 'groundhog', 'hedgehog', 'hen', 'hippo', 'horse', 'hyena', 'koala',
  'leopard', 'lion', 'llama', 'lobster', 'lynx', 'meerkat', 'mole', 'moose', 'moth', 'mouse', 'octopus',
  'orangutan', 'orca', 'ostrich', 'otter', 'owl', 'panda', 'panther', 'parrot', 'penguin', 'pig',
  'pigeon', 'rabbit', 'raccoon', 'reindeer', 'seagull', 'seahorse', 'seal', 'shark', 'sheep',
  'shrimp', 'slug', 'snail', 'snake', 'sparrow', 'spider', 'squid', 'squirrel', 'starfish', 'swan',
  'tiger', 'turtle', 'wallaby', 'walrus', 'wasp', 'weasel', 'weaver', 'whale', 'wolf', 'wolverine',
  'wombat'];

/**
 * Generates 'random' animal names.
 */
export function generateRandomName() {
  const adjective = adjectives[Math.floor(Math.random() * adjectives.length)];
  const animal = animals[Math.floor(Math.random() * animals.length)];
  return `${adjective}_${animal}`;
}

/**
 * Prints a date in ISO format (with Z suffix).
 * Dates or datetimes without Z suffix are considered UTC.
 */
export function toISOString(date: Date | string): string {
  let dateString;
  if (typeof date === 'string') {
    // Convert to date first, this standardizes output (millis precision)
    dateString = toDate(date).toISOString();
  } else {
    dateString = date.toISOString();
  }
  return dateString;
}

export function toDate(obj: any): Date {
  if (!obj) {
    return obj;
  }

  if (obj instanceof Date) {
    return obj;
  } else if (typeof obj === 'number') {
    return new Date(obj);
  } else if (typeof obj === 'string') {
    if (!obj.endsWith('Z')) {
      obj = obj + 'Z';
    }
    return new Date(Date.parse(obj));
  } else {
    throw new Error(`Cannot convert '${obj}' to Date`);
  }
}

export function toBase64URL(data: string) {
  return window.btoa(data)
    .replace(/\//g, '_')
    .replace(/\+/g, '-')
    .replace(/=/g, '');
}

export function fromBase64URL(base64Url: string) {
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
  return window.atob(base64);
}

export function generateUnsignedJWT(claims: { [key: string]: any; }) {
  const joseHeader = toBase64URL('{"alg":"none"}');
  const payload = toBase64URL(JSON.stringify(claims));
  return `${joseHeader}.${payload}.`;
}

export function lpad(nr: number, n: number) {
  return Array(n - String(nr).length + 1).join('0') + nr;
}

export function getDefaultProcessor(instance: Instance): string | null {
  if (!instance) {
    return null;
  }

  // Try to find a 'default' processor for this instance.
  // The alphabetic-first non-replay persistent processor
  for (const processor of (instance.processors || [])) {
    if (processor.persistent && !processor.replay) {
      return processor.name;
    }
  }

  return null;
}

/**
 * Prints a qualified name of a specific parameter member entry
 */
export function getMemberPath(parameter: Parameter): string | null {
  if (!parameter) {
    return null;
  }
  let result = parameter.qualifiedName;
  if (parameter.path) {
    for (let i = 0; i < parameter.path.length; i++) {
      const el = parameter.path[i];
      if (el.startsWith('[')) {
        result += el;
      } else {
        result += '.' + el;
      }
    }
  }
  return result;
}

/**
 * Outputs the basename of a path string (no extension).
 */
export function getBasename(path: string | null): string | null {
  if (!path) {
    return null;
  }

  const idx = path.lastIndexOf('.');
  if (idx === -1) {
    return path;
  } else {
    return path.substring(0, idx);
  }
}

/**
 * Outputs the filename of a path string. The path may end with a trailing slash which is preserved.
 */
export function getFilename(path: string): string | null {
  if (!path) {
    return null;
  }
  let idx = path.lastIndexOf('/');
  if (path.endsWith('/')) {
    idx = path.substring(0, path.length - 1).lastIndexOf('/');
  }

  if (idx === -1) {
    return path;
  } else {
    return path.substring(idx + 1);
  }
}

/**
 * Outputs the extension of a filename.
 */
export function getExtension(filename: string | null): string | null {
  if (!filename) {
    return null;
  }

  let idx = filename.lastIndexOf('.');
  if (idx === -1) {
    return null;
  } else {
    return filename.substring(idx + 1);
  }
}

export function getEntryForOffset(parameter: Parameter, offset: string): Parameter | ParameterMember | null {
  const entry = parameter.name + offset;
  const parts = entry.split('.');

  let node: Parameter | ParameterMember = parameter;
  for (let i = 1; i < parts.length; i++) {
    let memberNode;
    const members: ParameterMember[] = getParameterTypeForEntry(node)?.member || [];
    for (const member of members) {
      if (member.name === parts[i]) {
        memberNode = member;
        break;
      }
    }

    if (!memberNode) {
      return null;
    } else {
      node = memberNode;
    }
  }

  return node || null;
}

function getParameterTypeForEntry(entry: Parameter | ParameterMember) {
  const entryType = entry.type as ParameterType;
  if (entryType.arrayInfo) {
    return entryType.arrayInfo.type;
  } else {
    return entry.type;
  }
}

export function getParameterTypeForPath(parameter: Parameter, pathString?: string): ParameterType | null | undefined {
  if (!parameter) {
    return null;
  }
  let path = parameter.path;

  // Allow overriding the path (for when it is not contained
  // in the parameter definition)
  if (pathString !== undefined) {
    path = pathString.split('.');
  }

  if (!path) {
    return parameter.type;
  }
  let ptype = parameter.type!;
  for (const segment of path) {
    if (segment.startsWith('[')) {
      ptype = ptype.arrayInfo!.type;
    } else {
      for (const member of (ptype.member || [])) {
        if (member.name === segment) {
          ptype = member.type as ParameterType;
          break;
        }
      }
    }
  }
  return ptype;
}

export function getUnits(unitSet?: UnitInfo[]): string | null {
  if (!unitSet || unitSet.length === 0) {
    return null;
  }
  let res = '';
  for (const unitInfo of unitSet) {
    res += unitInfo.unit + ' ';
  }
  return res;
}

export function unflattenIndex(flatIndex: number, dimensions: number[]) {
  let n = flatIndex;

  let d = 1;
  for (let i = 1; i < dimensions.length; i++) {
    d *= dimensions[i];
  }

  let result = [];

  let k;
  for (k = 0; k < dimensions.length - 1; k++) {
    result[k] = Math.floor(n / d);
    n = n - d * result[k];
    d = Math.floor(d / dimensions[k + 1]);
  }
  result[k] = n;
  return result;
}

export function objectCompareFn(...fields: string[]) {
  fields = [...fields];
  const reverse: boolean[] = [];
  for (let i = 0; i < fields.length; i++) {
    if (fields[i].startsWith('-')) {
      reverse.push(true);
      fields[i] = fields[i].substring(1);
    } else {
      reverse.push(false);
    }
  }
  return (a: any, b: any) => {
    let rc = 0;
    for (let i = 0; i < fields.length; i++) {
      const field = fields[i];
      let aField = (a.hasOwnProperty(field) ? (a as any)[field] : null) ?? null;
      let bField = (b.hasOwnProperty(field) ? (b as any)[field] : null) ?? null;
      if (typeof aField === 'string') {
        aField = aField.toLowerCase();
      }
      if (typeof bField === 'string') {
        bField = bField.toLowerCase();
      }
      if (aField === bField) {
        rc = 0;
      } else if (aField === null) {
        rc = -1;
      } else if (bField == null) {
        rc = 1;
      } else {
        rc = (aField > bField) ? 1 : -1;
      }
      if (reverse[i]) {
        rc = -rc;
      }
      if (rc !== 0) {
        break;
      }
    }
    return rc;
  };
}
