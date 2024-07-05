import { DateAdapter } from '@angular/material/core';

const MONTH_NAMES = {
  'long': [
    'January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'
  ],
  'short': ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  'narrow': ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D']
};

const DATE_NAMES = Array(31);
for (let i = 0; i < DATE_NAMES.length; i++) {
  DATE_NAMES[i] = String(i + 1);
}

const DAY_OF_WEEK_NAMES = {
  'long': ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
  'short': ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
  'narrow': ['S', 'M', 'T', 'W', 'T', 'F', 'S']
};

const PARSE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * A date adapter based on NativeDateAdapter but that
 * formats as YYYY-MM-DD and uses UTC.
 */
export class UtcDateAdapter extends DateAdapter<Date> {

  getYear(date: Date): number {
    return date.getUTCFullYear();
  }

  getMonth(date: Date): number {
    return date.getUTCMonth();
  }

  getDate(date: Date): number {
    return date.getUTCDate();
  }

  getDayOfWeek(date: Date): number {
    return date.getUTCDay();
  }

  getMonthNames(style: 'long' | 'short' | 'narrow'): string[] {
    return MONTH_NAMES[style];
  }

  getDateNames(): string[] {
    return DATE_NAMES;
  }

  getDayOfWeekNames(style: 'long' | 'short' | 'narrow'): string[] {
    return DAY_OF_WEEK_NAMES[style];
  }

  getYearName(date: Date): string {
    return String(this.getYear(date));
  }

  getFirstDayOfWeek(): number {
    return 1;  // Monday
  }

  getNumDaysInMonth(date: Date): number {
    return this.getDate(this._createDateWithOverflow(
      this.getYear(date), this.getMonth(date) + 1, 0));
  }

  clone(date: Date): Date {
    return new Date(date.getTime());
  }

  createDate(year: number, month: number, date: number): Date {
    return this._createDateWithOverflow(year, month, date);
  }

  today(): Date {
    return new Date();
  }

  parse(value: any, parseFormat: any): Date | null {
    if (value) {
      const match = value.match(PARSE_PATTERN);
      if (match) {
        return new Date(Date.UTC(match[1], match[2] - 1, match[3]));
      }
    }
    return null;
  }

  // "Strict" parser (not used for user input, but maybe for form initial values)
  override deserialize(value: any): Date | null {
    let date;
    if (value instanceof Date) {
      date = this.clone(value);
    } else if (typeof value === 'string') {
      if (!value) {
        return null;
      }
      const match = value.match(PARSE_PATTERN);
      if (match) {
        date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
      } else {
        throw new Error(`Cannot parse '${value}' date string`);
      }
    }
    return super.deserialize(value);
  }

  format(date: Date, displayFormat: any): string {
    if (!this.isValid(date)) {
      throw new Error('Cannot format invalid date.');
    }
    return this.toIso8601(date);
  }

  addCalendarYears(date: Date, years: number): Date {
    return this.addCalendarMonths(date, years * 12);
  }

  addCalendarMonths(date: Date, months: number): Date {
    let newDate = this._createDateWithOverflow(
      this.getYear(date), this.getMonth(date) + months, this.getDate(date));

    // It's possible to wind up in the wrong month if the original month has more days than the new
    // month. In this case we want to go to the last day of the desired month.
    // Note: the additional + 12 % 12 ensures we end up with a positive number, since JS % doesn't
    // guarantee this.
    if (this.getMonth(newDate) != ((this.getMonth(date) + months) % 12 + 12) % 12) {
      newDate = this._createDateWithOverflow(this.getYear(newDate), this.getMonth(newDate), 0);
    }

    return newDate;
  }

  addCalendarDays(date: Date, days: number): Date {
    return this._createDateWithOverflow(
      this.getYear(date), this.getMonth(date), this.getDate(date) + days);
  }

  toIso8601(date: Date): string {
    return date.toISOString().substring(0, 10);
  }

  isDateInstance(obj: any): boolean {
    return obj instanceof Date;
  }

  isValid(date: Date): boolean {
    return !isNaN(date.getTime());
  }

  invalid(): Date {
    return new Date(NaN);
  }

  /** Creates a date but allows the month and date to overflow. */
  private _createDateWithOverflow(year: number, month: number, date: number) {
    const result = new Date(Date.UTC(year, month, date));

    // We need to correct for the fact that JS native Date treats years in range [0, 99] as
    // abbreviations for 19xx.
    if (year >= 0 && year < 100) {
      result.setUTCFullYear(this.getYear(result) - 1900);
    }
    return result;
  }
}
