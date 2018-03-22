import { Value } from "@yamcs/client";

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
    default:
      return null; // Assuming not a number
  }
}
