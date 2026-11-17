import React from 'react';
export { formatSalary } from '../utils/format';

const STATUS_CLASS: Record<string, string> = {
  OPEN: 'badge-green',
  COMPLETED: 'badge-green',
  HIRED: 'badge-green',
  OFFER: 'badge-purple',
  INTERVIEW: 'badge-blue',
  SCHEDULED: 'badge-blue',
  PROCESSING: 'badge-blue',
  SCREENING: 'badge-yellow',
  RETRYING: 'badge-yellow',
  PAUSED: 'badge-yellow',
  QUEUED: 'badge-gray',
  SUBMITTED: 'badge-gray',
  DRAFT: 'badge-gray',
  REJECTED: 'badge-red',
  DEAD_LETTER: 'badge-red',
  CLOSED: 'badge-red',
  CANCELLED: 'badge-red',
  WITHDRAWN: 'badge-gray',
};

export const StatusBadge: React.FC<{ status: string }> = ({ status }) => {
  const cls = STATUS_CLASS[status] ?? 'badge-gray';
  return <span className={`badge ${cls}`}>{status.replace('_', ' ')}</span>;
};
