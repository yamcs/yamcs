import { CommandId, Value } from '@yamcs/client';
const PREVIEW_LENGTH = 5;

export type ISOResolution = 'day' | 'hhmm' | 'hhmmss' | 'millis';

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
  // tslint:disable-next-line:max-line-length
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

/**
 * Converts an ISO duration string to the equivalent number of milliseconds.
 * This only works with seconds, minutes, hours and days.
 */
export function convertDurationToMillis(isoDuration: string) {
  // tslint:disable-next-line:max-line-length
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
  const raw = atob(base64);
  let result = '';
  for (let i = 0; i < raw.length; i++) {
    const hex = raw.charCodeAt(i).toString(16);
    result += (hex.length === 2 ? hex : '0' + hex);
  }
  return result;
}

const adjectives = [
  'amused', 'acid', 'adaptable', 'alleged', 'agreeable', 'aspiring', 'berserk', 'bright',
  'busy', 'calm', 'caring', 'chilly', 'cool', 'curious', 'dapper', 'dazzling', 'dizzy', 'eager',
  'elite', 'energetic', 'familiar', 'famous', 'fancy', 'fast', 'festive', 'flawless', 'fresh',
  'friendly', 'funny', 'furry', 'gifted', 'groovy', 'helpful', 'hungry', 'jolly', 'jumpy', 'lucky',
  'quick', 'quiet', 'rapid', 'rare', 'scary', 'swift', 'tall', 'tame', 'tidy', 'thirsty', 'tough',
  'wacky', 'wild'];

const animals = [
  'alligator', 'ant', 'anteater', 'antelope', 'armadillo', 'badger', 'bat', 'bear', 'bee',
  'beetle', 'buffalo', 'butterfly', 'camel', 'cat', 'chameleon', 'cheetah', 'chicken',
  'cicada', 'clam', 'cow', 'coyote', 'crab', 'cricket', 'crow', 'deer', 'dog', 'dolphin', 'donkey',
  'dove', 'dragonfly', 'duck', 'eagle', 'eel', 'elephant', 'ferret', 'fish', 'fly', 'fox', 'frog',
  'gazelle', 'goat', 'groundhog', 'hedgehog', 'hen', 'hippo', 'horse', 'hyena', 'koala', 'leopard',
  'lion', 'llama', 'lobster', 'lynx', 'meerkat', 'mole', 'moose', 'moth', 'mouse', 'octopus',
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

export function printValue(value: Value) {
  if (value.type === 'AGGREGATE') {
    let preview = '{';
    if (value.aggregateValue) {
      const n = Math.min(value.aggregateValue.name.length, PREVIEW_LENGTH);
      for (let i = 0; i < n; i++) {
        if (i !== 0) {
          preview += ', ';
        }
        preview += value.aggregateValue.name[i] + ': ' + printValueWithoutPreview(value.aggregateValue.value[i]);
      }
      if (n < value.aggregateValue.value.length) {
        preview += `, …`;
      }
    }
    return preview + '}';
  } else if (value.type === 'ARRAY') {
    let preview = '[';
    if (value.arrayValue) {
      const n = Math.min(value.arrayValue.length, PREVIEW_LENGTH);
      for (let i = 0; i < n; i++) {
        if (i !== 0) {
          preview += ', ';
        }
        preview += printValueWithoutPreview(value.arrayValue[i]);
      }
      if (n < value.arrayValue.length) {
        preview += ', …';
      }
      preview += `] (${value.arrayValue.length})`;
    } else {
      preview += '] (0)';
    }
    return preview;
  } else {
    return printValueWithoutPreview(value);
  }
}

function printValueWithoutPreview(value: Value): string {
  switch (value.type) {
    case 'AGGREGATE':
      return 'aggregate';
    case 'ARRAY':
      return 'array';
    case 'BOOLEAN':
      return '' + value.booleanValue;
    case 'FLOAT':
      return '' + value.floatValue;
    case 'DOUBLE':
      return '' + value.doubleValue;
    case 'UINT32':
      return '' + value.uint32Value;
    case 'SINT32':
      return '' + value.sint32Value;
    case 'BINARY':
      return '<binary>';
    case 'ENUMERATED':
    case 'STRING':
      return value.stringValue!;
    case 'TIMESTAMP':
      return printDateTime(value.stringValue!);
    case 'UINT64':
      return '' + value.uint64Value;
    case 'SINT64':
      return '' + value.sint64Value;
    default:
      return 'Unsupported data type';
  }
}

export function printCommandId(commandId: CommandId): string {
  let id = String(commandId.generationTime);
  if (commandId.origin) {
    id += '-' + commandId.origin;
  }
  return id + '-' + commandId.sequenceNumber;
}

export function printDateTime(date: Date | string, addTimezone = true): string {
  let dateString;
  if (typeof date === 'string') {
    // Convert to date first, this standardizes output (millis precision)
    dateString = toDate(date).toISOString();
  } else {
    dateString = date.toISOString();
  }
  return dateString.replace('T', ' ').replace('Z', addTimezone ? ' UTC' : '');
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

/**
 * Prints a date in ISO format without any timezone indication.
 *
 * For example:
 * - 'day' resolution: 2050-12-20
 * - 'hhmm' resolution: 2050-12-20T08:06
 * - 'millis' resolution: 2050-12-20T08:06:00.123
 */
export function printLocalDate(date: Date | string, resolution: ISOResolution = 'millis'): string | null {
  if (!date) {
    return null;
  }
  let dateString;
  if (typeof date === 'string') {
    // Convert to date first, this standardizes output (millis precision)
    dateString = toDate(date).toISOString();
  } else {
    dateString = date.toISOString();
  }
  switch (resolution) {
    case 'day':
      return dateString.substr(0, 10);
    case 'hhmm':
      return dateString.substr(0, 16);
    case 'hhmmss':
      return dateString.substr(0, 19);
    case 'millis':
      return dateString;
    default:
      return `Unexpected resolution ${resolution}`;
  }
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
