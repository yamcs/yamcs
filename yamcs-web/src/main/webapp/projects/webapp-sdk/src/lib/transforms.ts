export function stringArrayAttribute(value: unknown): string[] {
  if (!value) {
    return [];
  } else if (Array.isArray(value)) {
    return value;
  } else {
    return [value as any];
  }
}
