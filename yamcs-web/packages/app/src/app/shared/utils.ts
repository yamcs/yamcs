import { Value } from '@yamcs/client';

/**
 * Deep clones an object.
 * https://github.com/whatwg/html/issues/793
 */
export function structuredClone(obj: {}) {
  return new Promise(resolve => {
    const {port1, port2} = new MessageChannel();
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
  'shrimp', 'slug', 'snail', 'snake', 'sparrow', 'squid', 'squirrel', 'starfish', 'swan', 'tiger',
  'turtle', 'wallaby', 'walrus', 'wasp', 'weasel', 'weaver', 'whale', 'wolf', 'wolverine', 'wombat'];

/**
 * Generates 'random' animal names.
 */
export function generateRandomName() {
  const adjective = adjectives[Math.floor(Math.random() * adjectives.length)];
  const animal = animals[Math.floor(Math.random() * animals.length)];
  return `${adjective}_${animal}`;
}
