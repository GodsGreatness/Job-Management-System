export function formatSalary(min?: number, max?: number): string {
  if (!min && !max) return 'Competitive';
  const fmt = (n: number) => `$${Math.round(n / 1000)}k`;
  if (min && max) return `${fmt(min)} – ${fmt(max)}`;
  return min ? `${fmt(min)}+` : `Up to ${fmt(max!)}`;
}
