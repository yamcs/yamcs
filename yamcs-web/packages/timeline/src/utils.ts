// Incrementing id for uniquely identifying something across contributions, or timelines
let idCounter = 0;

function leftPad(nr: number, n: number) {
  return Array(n - String(nr).length + 1).join('0') + nr;
}

/**
 * Returns a 'unique' identifier
 */
export function generateId(): string {
  const newId = 'tlid' + idCounter;
  idCounter += 1;
  return newId;
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
    return new Date(Date.parse(obj));
  } else {
    throw new Error(`Cannot convert '${obj}' to Date`);
  }
}

export function addDays(date: Date, days: number) {
  const result = new Date(date.getTime());
  result.setDate(result.getDate() + days);
  return result;
}

export function addSeconds(date: Date, seconds: number) {
  return new Date(date.getTime() + (seconds * 1000));
}

export function addMillis(date: Date, millis: number) {
  return new Date(date.getTime() + millis);
}

/**
 * Returns the number of milliseconds between the two specified
 * dates. The sign of the returned value will be negative if
 * date1 comes after date2.
 */
export function millisBetween(date1: Date, date2: Date) {
  return date2.getTime() - date1.getTime();
}

export function isBefore(date1: Date, date2: Date) {
  return date1.getTime() < date2.getTime();
}

export function isAfter(date1: Date, date2: Date) {
  return date1.getTime() > date2.getTime();
}

export function isBetween(date: Date, intvStart: Date, intvStop: Date) {
  return intvStart.getTime() <= date.getTime() && date.getTime() <= intvStop.getTime();
}

export function startOfHour(date: Date) {
  const truncated = new Date(date.getTime());
  truncated.setMinutes(0, 0, 0);
  return truncated;
}

export function startOfDay(date: Date, tz?: string) {
  const utc = (tz === 'GMT' || tz === 'UTC');
  const truncated = new Date(date.getTime());
  if (utc) {
    truncated.setUTCHours(0, 0, 0, 0);
  } else {
    truncated.setHours(0, 0, 0, 0);
  }
  return truncated;
}

export function startOfWeek(date: Date, tz?: string) {
  const utc = (tz === 'GMT' || tz === 'UTC');
  const truncated = startOfDay(date, tz);
  if (utc) {
    const day = truncated.getUTCDay() || 7; // Turn Sunday into 7
    if (day !== 1) {
      truncated.setUTCHours(-24 * (day - 1));
    }
  } else {
    const day = truncated.getDay() || 7; // Turn Sunday into 7
    if (day !== 1) {
      truncated.setHours(-24 * (day - 1));
    }
  }
  return truncated;
}

export function startOfMonth(date: Date, tz?: string) {
  const utc = (tz === 'GMT' || tz === 'UTC');
  const truncated = new Date(date.getTime());
  if (utc) {
    truncated.setUTCDate(1);
    truncated.setUTCHours(0, 0, 0, 0);
  } else {
    truncated.setDate(1);
    truncated.setHours(0, 0, 0, 0);
  }
  return truncated;
}

export function startOfYear(date: Date, tz?: string) {
  const utc = (tz === 'GMT' || tz === 'UTC');
  if (utc) {
    return new Date(Date.UTC(date.getUTCFullYear(), 0, 1, 0, 0, 0, 0));
  } else {
    return new Date(date.getFullYear(), 0, 1, 0, 0, 0, 0);
  }
}

export function isLeapYear(date: Date, utc: boolean) {
  const year = (utc ? date.getUTCFullYear() : date.getFullYear());
  if ((year & 3) !== 0) {
    return false;
  }
  return ((year % 100) !== 0 || (year % 400) === 0);
}

export function formatDate(date: Date, format: string, tz?: string) {
  const utc = (tz === 'GMT' || tz === 'UTC');
  if (tz && !utc) {
    throw new Error(`Unsupported timezone '${tz}'`);
  }

  if (format === 'YYYY') {
    return String(utc ? date.getUTCFullYear() : date.getFullYear());
  } else if (format === 'MM') {
    const m = (utc ? date.getUTCMonth() : date.getMonth()) + 1;
    return (m < 10) ? '0' + m : ' ' + m;
  } else if (format === 'MMM') {
    const monthAbbr = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const m = (utc ? date.getUTCMonth() : date.getMonth());
    return monthAbbr[m];
  } else if (format === 'MMMM') {
    const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    const m = (utc ? date.getUTCMonth() : date.getMonth());
    return months[m];
  } else if (format === 'DD') {
    const d = (utc ? date.getUTCDate() : date.getDate());
    return (d < 10) ? '0' + d : ' ' + d;
  } else if (format === 'DDDD') { // Day of year
    const dayCount = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
    const m = (utc ? date.getUTCMonth() : date.getMonth());
    const d = (utc ? date.getUTCDate() : date.getDate());
    let dayOfYear = dayCount[m] + d;
    if (m > 1 && isLeapYear(date, utc)) {
      dayOfYear++;
    }
    return leftPad(dayOfYear, 3);
  } else if (format === 'dd') { // Day of week
    const weekAbbr = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];
    return weekAbbr[utc ? date.getUTCDay() : date.getDay()];
  } else if (format === 'ddd') { // Day of week
    const weekAbbr = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    return weekAbbr[utc ? date.getUTCDay() : date.getDay()];
  } else if (format === 'Do') {
    const d = (utc ? date.getUTCDate() : date.getDate());
    return (d === 1 || d === 31) ? d + 'st' : (d === 2) ? d + 'nd' : d + 'th';
  } else if (format === 'HH') {
    const h = (utc ? date.getUTCHours() : date.getHours());
    return (h < 10) ? '0' + h : '' + h;
  } else {
    throw new Error(`Unexpected format '${format}'`);
  }
}

/**
 *  Returns the rendered pixel width of the element.
 *  This does not include margin, padding or borders.
 */
export function getWidth(el: HTMLElement) {
  // should maybe use offsetWidth
  return el.getBoundingClientRect().width;
}

/**
 * Simple object check.
 */
export function isObject(item: any) {
  return (item && typeof item === 'object' && !Array.isArray(item));
}

/**
 * Deep merge two objects.
 */
export function mergeDeep(target: any, ...sources: any[]): any {
  if (!sources.length) {
    return target;
  }
  const source = sources.shift();

  if (isObject(target) && isObject(source)) {
    for (const key in source) {
      if (isObject(source[key])) {
        if (!target[key]) {
          target[key] = {};
        }
        mergeDeep(target[key], source[key]);
      } else {
        target[key] = source[key];
      }
    }
  }

  return mergeDeep(target, ...sources);
}
