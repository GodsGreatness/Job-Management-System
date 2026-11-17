export type Role = 'CANDIDATE' | 'EMPLOYER' | 'RECRUITER' | 'ADMIN';

export type JobStatus = 'DRAFT' | 'OPEN' | 'PAUSED' | 'CLOSED';
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERNSHIP';
export type ApplicationStatus =
  | 'SUBMITTED'
  | 'SCREENING'
  | 'INTERVIEW'
  | 'OFFER'
  | 'HIRED'
  | 'REJECTED'
  | 'WITHDRAWN';
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type BackgroundJobType =
  | 'SEND_EMAIL'
  | 'PROCESS_RESUME'
  | 'SEND_NOTIFICATION'
  | 'REFRESH_ANALYTICS';
export type BackgroundJobStatus =
  | 'QUEUED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'RETRYING'
  | 'DEAD_LETTER';

export interface UserSummary {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  companyName?: string;
  headline?: string;
  skills?: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInMs: number;
  user: UserSummary;
}

export interface JobPosting {
  id: number;
  title: string;
  company: string;
  department?: string;
  location: string;
  remote: boolean;
  employmentType: EmploymentType;
  salaryMin?: number;
  salaryMax?: number;
  description: string;
  requiredSkills: string[];
  status: JobStatus;
  employerId: number;
  employerName: string;
  applicantCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Application {
  id: number;
  jobId: number;
  jobTitle: string;
  company: string;
  candidateId: number;
  candidateName: string;
  candidateEmail: string;
  status: ApplicationStatus;
  resumeText: string;
  coverLetter?: string;
  atsMatchScore?: number;
  matchedSkills: string[];
  missingSkills: string[];
  recruiterNotes?: string;
  appliedAt: string;
  updatedAt: string;
}

export interface Interview {
  id: number;
  applicationId: number;
  jobId: number;
  jobTitle: string;
  candidateName: string;
  candidateEmail: string;
  interviewerId: number;
  interviewerName: string;
  scheduledAt: string;
  durationMinutes: number;
  meetingLink?: string;
  stage: string;
  status: InterviewStatus;
  feedback?: string;
  rating?: number;
  createdAt: string;
}

export interface NotificationItem {
  id: number;
  title: string;
  message: string;
  category: string;
  read: boolean;
  createdAt: string;
}

export interface SystemAnalytics {
  totalUsers: number;
  totalCandidates: number;
  totalEmployers: number;
  totalRecruiters: number;
  totalJobs: number;
  openJobs: number;
  totalApplications: number;
  scheduledInterviews: number;
  averageAtsScore: number;
  applicationsByStatus: Record<string, number>;
  computedAt: string;
}

export interface QueueMetrics {
  workerPoolSize: number;
  activeWorkers: number;
  queuedCount: number;
  processingCount: number;
  retryingCount: number;
  completedCount: number;
  deadLetterCount: number;
  averageExecutionMs: number;
  jobsByType: Record<string, number>;
  snapshotAt: string;
}

export interface BackgroundJob {
  id: number;
  type: BackgroundJobType;
  status: BackgroundJobStatus;
  priority: number;
  payload: string;
  idempotencyKey?: string;
  attempts: number;
  maxAttempts: number;
  nextRunAt: string;
  lockedBy?: string;
  executionDurationMs?: number;
  lastError?: string;
  resultSummary?: string;
  createdAt: string;
  completedAt?: string;
}
