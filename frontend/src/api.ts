export interface Assignment {
  id: string
  platform: string
  course: string
  title: string
  status: string
  deadline: string
  sourceUrl: string
  timed: boolean
  timeLimitMinutes?: number
  note?: string
}

export interface ManualAssignmentPayload {
  course: string
  title: string
  status: string
  deadline: string
  timed: boolean
  timeLimitMinutes?: number
  note: string
}

export interface AssignmentListResponse {
  source: string
  status: string
  message: string
  syncedAt: string
  assignments: Assignment[]
}

export interface LlmProvider {
  provider: string
  displayName: string
  defaultModel: string
  configured: boolean
}

export interface ModelSettings {
  provider: string
  baseUrl: string
  apiKey: string
  model: string
  apiPath?: string
  authType?: string
  configured?: boolean
  resolvedUrl?: string
  apiKeyPreview?: string
  updatedBy?: string
}

export interface LlmConnectionTestResponse {
  ok: boolean
  provider: string
  model: string
  resolvedUrl: string
  message: string
  checkedAt: string
}

export interface SkillDefinition {
  name: string
  title: string
  description: string
  triggerWords: string[]
  enabled: boolean
  parameters?: SkillParameterDefinition[]
  sourceUrl?: string
  signature?: string
  signatureStatus?: string
}

export interface SkillParameterDefinition {
  name: string
  label: string
  type: 'text' | 'number' | 'select' | string
  placeholder?: string
  required: boolean
  defaultValue?: string
  options?: string[]
}

export interface SkillInvocationHint {
  skill: SkillDefinition
  prompt: string
}

export interface FeishuHealthResponse {
  status: string
  callbackPath: string
  publicCallbackUrl?: string
  requestUrl?: string
  botCredentialsReady: boolean
  verificationTokenConfigured: boolean
  defaultChatConfigured: boolean
  replyMode: string
  recommendedNextFeatures: string[]
}

export interface AgentResponse {
  answer: string
  plannedSkills: string[]
  data: {
    assignments?: Assignment[]
    relatedAssignments?: Assignment[]
    networkReport?: NetworkMeasureReport
    artifacts?: AgentArtifact[]
  }
  createdAt: string
}

export interface AgentArtifact {
  id: string
  filename: string
  type: string
  url: string
  sizeBytes: number
}

export interface AgentRunRecord {
  id: string
  channel: string
  userId?: string
  chatId?: string
  command: string
  answer: string
  plannedSkills: string[]
  createdAt: string
}

export interface SkillExecutionRecord {
  id: string
  runId: string
  skillName: string
  skillTitle: string
  status: string
  parameters: Record<string, unknown>
  summary: string
  durationMs: number
  createdAt: string
}

export interface SecurityHeaderResult {
  name: string
  present: boolean
  value: string
  meaning: string
  recommendation: string
}

export interface NetworkMeasureReport {
  id: string
  target: string
  finalUrl: string
  host: string
  ipAddresses: string[]
  checkedAt: string
  reachable: boolean
  httpsEnabled: boolean
  httpStatus?: number
  httpStatusText?: string
  dnsMs?: number
  tcpMs?: number
  tlsMs?: number
  ttfbMs?: number
  totalMs?: number
  responseBytes?: number
  certificateValid: boolean
  certificateSubject: string
  certificateIssuer: string
  certificateValidFrom?: string
  certificateValidTo?: string
  certificateDaysRemaining?: number
  securityHeaders: SecurityHeaderResult[]
  risks: string[]
  riskLevel: string
  summary: string
}

export interface NetworkListenerEvent {
  id: string
  capturedAt: string
  method: string
  path: string
  queryString: string
  sourceIp: string
  userAgent: string
  contentType: string
  contentLength: number
  headers: Record<string, string>
  bodyPreview: string
  riskHints: string[]
}

export interface NetworkListenerStatus {
  enabled: boolean
  capturePath: string
  captureUrl: string
  totalEvents: number
  lastCapturedAt: string
  capabilities: string[]
}

function resolveDefaultApiBase() {
  if (typeof window === 'undefined') {
    return 'http://localhost:8090'
  }

  const hostname = window.location.hostname
  const isLocalHost = hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1'

  if (isLocalHost && window.location.port !== '8090') {
    return `${window.location.protocol}//${hostname}:8090`
  }

  return window.location.origin
}

const DEFAULT_API_BASE = resolveDefaultApiBase()

const API_BASE = (import.meta.env.VITE_API_BASE || DEFAULT_API_BASE).replace(/\/$/, '')

const demoAssignments: Assignment[] = [
  {
    id: 'local-rain-001',
    platform: '\u96e8\u8bfe\u5802',
    course: '\u673a\u5668\u5b66\u4e60',
    title: '\u7b2c\u4e09\u5468\u68af\u5ea6\u4e0b\u964d\u5b9e\u9a8c\u62a5\u544a',
    status: '\u5f85\u5b8c\u6210',
    deadline: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString(),
    sourceUrl: 'https://www.yuketang.cn/',
    timed: false,
    note: '',
  },
  {
    id: 'local-rain-002',
    platform: '\u96e8\u8bfe\u5802',
    course: '\u5927\u5b66\u82f1\u8bed',
    title: 'Unit 5 \u9605\u8bfb\u6d4b\u9a8c',
    status: '\u8fdb\u884c\u4e2d',
    deadline: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString(),
    sourceUrl: 'https://www.yuketang.cn/',
    timed: true,
    timeLimitMinutes: 45,
    note: '',
  },
]

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const targetUrl = url.startsWith('http') ? url : `${API_BASE}${url}`
  let response: Response

  try {
    response = await fetch(targetUrl, {
      headers: {
        'Content-Type': 'application/json',
        ...(options?.headers ?? {}),
      },
      ...options,
    })
  } catch (error) {
    throw new Error(
      `\u8fde\u63a5\u540e\u7aef\u5931\u8d25\uff1a${targetUrl}\n\u8bf7\u786e\u8ba4 8090 \u540e\u7aef\u670d\u52a1\u6b63\u5728\u8fd0\u884c\u3002`,
    )
  }

  if (!response.ok) {
    const detail = await response.text().catch(() => '')
    throw new Error(`\u8bf7\u6c42\u5931\u8d25\uff1a${response.status}${detail ? `\n${detail}` : ''}`)
  }

  return response.json() as Promise<T>
}

export async function fetchAssignments() {
  return request<AssignmentListResponse>('/api/academic/assignments')
}

export async function fetchLlmProviders() {
  return request<LlmProvider[]>('/api/llm/providers')
}

export async function fetchLlmSettings() {
  return request<ModelSettings>('/api/llm/settings')
}

export async function saveLlmSettings(settings: ModelSettings) {
  return request<ModelSettings>('/api/llm/settings', {
    method: 'PUT',
    body: JSON.stringify(settings),
  })
}

export async function testLlmSettings(settings: ModelSettings) {
  const payload: Partial<ModelSettings> = { ...settings }
  if (!payload.apiKey?.trim()) {
    delete payload.apiKey
  }

  return request<LlmConnectionTestResponse>('/api/llm/test', {
    method: 'POST',
    body: JSON.stringify({
      ...payload,
      message: '请只回复：OK',
    }),
  })
}

export async function fetchSkills() {
  try {
    return await request<SkillDefinition[]>('/api/agent/skills')
  } catch {
    return [
      {
        name: 'rain_classroom_sync',
        title: '\u96e8\u8bfe\u5802\u540c\u6b65',
        description: '\u540c\u6b65\u96e8\u8bfe\u5802\u7b49\u5e73\u53f0\u7684\u4f5c\u4e1a\u3001\u622a\u6b62\u65f6\u95f4\u548c\u63d0\u4ea4\u72b6\u6001\u3002',
        triggerWords: ['\u540c\u6b65', '\u96e8\u8bfe\u5802', '\u4f5c\u4e1a'],
        enabled: true,
      },
      {
        name: 'assignment_planner',
        title: '\u5b66\u4e60\u8ba1\u5212',
        description: '\u628a\u4f5c\u4e1a\u62c6\u89e3\u6210\u4eca\u5929\u3001\u672c\u5468\u548c\u622a\u6b62\u524d\u7684\u6267\u884c\u8ba1\u5212\u3002',
        triggerWords: ['\u8ba1\u5212', '\u62c6\u89e3', '\u5b89\u6392'],
        enabled: true,
      },
      {
        name: 'llm_chat',
        title: '\u6a21\u578b\u95ee\u7b54',
        description: '\u8c03\u7528\u5f53\u524d\u914d\u7f6e\u7684\u5927\u6a21\u578b\u5b8c\u6210\u95ee\u7b54\u3001\u603b\u7ed3\u548c\u89c4\u5212\u3002',
        triggerWords: ['\u95ee', '\u603b\u7ed3', '\u590d\u4e60'],
        enabled: true,
      },
      {
        name: 'feishu_notify',
        title: '\u98de\u4e66\u901a\u77e5',
        description: '\u901a\u8fc7\u98de\u4e66\u53d1\u9001\u4f5c\u4e1a\u63d0\u9192\u548c\u6267\u884c\u7ed3\u679c\u3002',
        triggerWords: ['\u98de\u4e66', '\u63d0\u9192', '\u901a\u77e5'],
        enabled: true,
      },
    ]
  }
}

export async function fetchSkillMarketplace() {
  return request<SkillDefinition[]>('/api/agent/skills/marketplace')
}

export async function importSkill(json: string, signature = '') {
  return request<SkillDefinition>('/api/agent/skills/import', {
    method: 'POST',
    body: JSON.stringify({ json, signature }),
  })
}

export async function downloadSkill(sourceUrl: string) {
  return request<SkillDefinition>('/api/agent/skills/download', {
    method: 'POST',
    body: JSON.stringify({ sourceUrl }),
  })
}

export async function fetchSkillInvocationHint(skillName: string) {
  return request<SkillInvocationHint>(`/api/agent/skills/${encodeURIComponent(skillName)}/invoke`)
}

export async function fetchSkillExecutions(skillName = '', limit = 30) {
  const query = new URLSearchParams({ limit: String(limit) })
  if (skillName) query.set('skillName', skillName)
  return request<SkillExecutionRecord[]>(`/api/agent/skill-executions?${query.toString()}`)
}

export async function syncRainClassroom() {
  try {
    return await request<AssignmentListResponse>('/api/academic/sync/rain-classroom', {
      method: 'POST',
    })
  } catch {
    return {
      source: 'demo',
      status: 'demo',
      message:
        '\u540e\u7aef\u6682\u4e0d\u53ef\u7528\uff0c\u5df2\u663e\u793a\u524d\u7aef\u6f14\u793a\u4f5c\u4e1a\u3002\u63a5\u5165\u96e8\u8bfe\u5802 Cookie \u6216\u6d4f\u89c8\u5668\u81ea\u52a8\u5316\u540e\u53ef\u8bfb\u53d6\u771f\u5b9e\u4f5c\u4e1a\u3002',
      syncedAt: new Date().toISOString(),
      assignments: demoAssignments,
    }
  }
}

export async function createAssignment(payload: ManualAssignmentPayload) {
  return request<Assignment>('/api/academic/assignments', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function fetchFeishuHealth() {
  return request<FeishuHealthResponse>('/api/feishu/health')
}

export async function measureNetworkTarget(url: string, checks = ['dns', 'tcp', 'tls', 'headers', 'http']) {
  return request<NetworkMeasureReport>('/api/network/measure', {
    method: 'POST',
    body: JSON.stringify({ url, checks, samples: 1 }),
  })
}

export async function fetchNetworkReports(limit = 20) {
  return request<NetworkMeasureReport[]>(`/api/network/reports?limit=${limit}`)
}

export async function exportNetworkReport(id: string, format: 'docx' | 'xlsx' | 'pdf' | 'csv' = 'docx') {
  return request<AgentArtifact>(`/api/network/reports/${encodeURIComponent(id)}/export?format=${format}`, {
    method: 'POST',
  })
}

export async function exportNetworkComparisonReport(
  leftId: string,
  rightId: string,
  format: 'docx' | 'xlsx' | 'pdf' | 'csv' = 'docx',
) {
  const query = new URLSearchParams({ leftId, rightId, format })
  return request<AgentArtifact>(`/api/network/reports/compare/export?${query.toString()}`, {
    method: 'POST',
  })
}

export async function fetchNetworkListenerStatus() {
  return request<NetworkListenerStatus>('/api/network/listener')
}

export async function fetchNetworkListenerEvents(limit = 50) {
  return request<NetworkListenerEvent[]>(`/api/network/listener/events?limit=${limit}`)
}

export async function clearNetworkListenerEvents() {
  return request<{ cleared: boolean; timestamp: string }>('/api/network/listener/events', {
    method: 'DELETE',
  })
}

export async function exportNetworkListenerEvents(format: 'docx' | 'xlsx' | 'csv' = 'xlsx') {
  return request<AgentArtifact>(`/api/network/listener/export?format=${format}`, {
    method: 'POST',
  })
}

export async function deleteAssignment(id: string) {
  return request<{ deleted: boolean }>(`/api/academic/assignments/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}

export async function sendFeishuReminder(assignmentId: string) {
  return request<{ sent: boolean; channel: string; preview: string; createdAt: string }>('/api/academic/feishu/reminders', {
    method: 'POST',
    body: JSON.stringify({ assignmentId }),
  })
}

export async function fetchAgentEvents(channel = 'feishu', limit = 20) {
  return request<AgentRunRecord[]>(`/api/agent/events?channel=${encodeURIComponent(channel)}&limit=${limit}`)
}

export async function runAgent(
  command: string,
  settings: ModelSettings,
  skillName?: string,
  skillParameters: Record<string, unknown> = {},
) {
  const context: Record<string, unknown> = {
    llmProvider: settings.provider,
    llmBaseUrl: settings.baseUrl,
    llmModel: settings.model,
    apiPath: settings.apiPath,
    authType: settings.authType,
    skillName,
  }
  if (settings.apiKey?.trim()) {
    context.apiKey = settings.apiKey.trim()
  }

  return request<AgentResponse>('/api/agent/run', {
    method: 'POST',
    body: JSON.stringify({
      command,
      channel: 'web',
      userId: 'local-web',
      chatId: 'web-session',
      context,
      skillParameters,
    }),
  })
}
