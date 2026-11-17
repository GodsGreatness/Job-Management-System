import type {
  Application,
  ApplicationStatus,
  AuthResponse,
  BackgroundJob,
  BackgroundJobStatus,
  BackgroundJobType,
  EmploymentType,
  Interview,
  InterviewStatus,
  JobPosting,
  JobStatus,
  NotificationItem,
  QueueMetrics,
  Role,
  SystemAnalytics,
  UserSummary,
} from '../types';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

export function getStoredToken(): string | null {
  return localStorage.getItem('jms_token');
}

export function setStoredToken(token: string | null) {
  if (token) {
    localStorage.setItem('jms_token', token);
  } else {
    localStorage.removeItem('jms_token');
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getStoredToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (res.status === 204) {
    return undefined as T;
  }

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.message || `Request failed with status ${res.status}`);
  }
  return data as T;
}

export const api = {
  // Auth
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  register: (payload: {
    email: string;
    password: string;
    fullName: string;
    role: Role;
    companyName?: string;
    headline?: string;
    skills?: string;
  }) =>
    request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  me: () => request<UserSummary>('/api/auth/me'),

  // Jobs
  searchJobs: (params?: { status?: JobStatus; remote?: boolean; employmentType?: EmploymentType; keyword?: string }) => {
    const q = new URLSearchParams();
    if (params?.status) q.set('status', params.status);
    if (params?.remote !== undefined) q.set('remote', String(params.remote));
    if (params?.employmentType) q.set('employmentType', params.employmentType);
    if (params?.keyword) q.set('keyword', params.keyword);
    const qs = q.toString();
    return request<JobPosting[]>(`/api/jobs${qs ? `?${qs}` : ''}`);
  },

  myJobs: () => request<JobPosting[]>('/api/jobs/mine'),

  createJob: (payload: {
    title: string;
    company: string;
    department?: string;
    location: string;
    remote: boolean;
    employmentType: EmploymentType;
    salaryMin?: number;
    salaryMax?: number;
    description: string;
    requiredSkills: string;
  }) =>
    request<JobPosting>('/api/jobs', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateJob: (id: number, payload: Partial<{ status: JobStatus; title: string; location: string }>) =>
    request<JobPosting>(`/api/jobs/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    }),

  // Applications
  apply: (payload: { jobId: number; resumeText: string; coverLetter?: string }) =>
    request<Application>('/api/applications', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listApplications: (jobId?: number) =>
    request<Application[]>(`/api/applications${jobId ? `?jobId=${jobId}` : ''}`),

  updateApplicationStatus: (id: number, status: ApplicationStatus, recruiterNotes?: string) =>
    request<Application>(`/api/applications/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status, recruiterNotes }),
    }),

  // Interviews
  listInterviews: () => request<Interview[]>('/api/interviews'),

  scheduleInterview: (payload: {
    applicationId: number;
    scheduledAt: string;
    durationMinutes: number;
    stage: string;
    meetingLink?: string;
  }) =>
    request<Interview>('/api/interviews', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateInterview: (id: number, payload: { status?: InterviewStatus; feedback?: string; rating?: number }) =>
    request<Interview>(`/api/interviews/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    }),

  // Notifications
  listNotifications: () => request<NotificationItem[]>('/api/notifications'),
  markAllNotificationsRead: () => request<void>('/api/notifications/read-all', { method: 'POST' }),

  // Analytics
  getAnalytics: (live = false) => request<SystemAnalytics>(`/api/analytics/overview?live=${live}`),
  refreshAnalytics: () => request<BackgroundJob>('/api/analytics/refresh', { method: 'POST' }),

  // Admin Queue & Workers
  getQueueMetrics: () => request<QueueMetrics>('/api/admin/queue/metrics'),
  listQueueJobs: (status?: BackgroundJobStatus, limit = 50) => {
    const q = new URLSearchParams({ limit: String(limit) });
    if (status) q.set('status', status);
    return request<BackgroundJob[]>(`/api/admin/queue/jobs?${q.toString()}`);
  },
  triggerJob: (payload: { type: BackgroundJobType; priority: number; payload: string; simulateFailure: boolean }) =>
    request<BackgroundJob>('/api/admin/queue/jobs/trigger', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  replayJob: (id: number) => request<BackgroundJob>(`/api/admin/queue/jobs/${id}/replay`, { method: 'POST' }),
  flushQueue: () => request<{ status: string; metrics: QueueMetrics }>('/api/admin/queue/flush', { method: 'POST' }),
  listUsers: () => request<UserSummary[]>('/api/admin/users'),
};
