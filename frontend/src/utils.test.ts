import { describe, expect, it } from 'vitest';
import { formatSalary } from './utils/format';

describe('formatSalary', () => {
  it('formats min and max compensation range', () => {
    expect(formatSalary(95000, 135000)).toBe('$95k – $135k');
  });

  it('formats min-only compensation', () => {
    expect(formatSalary(80000, undefined)).toBe('$80k+');
  });

  it('returns Competitive when bounds omitted', () => {
    expect(formatSalary(undefined, undefined)).toBe('Competitive');
  });
});
