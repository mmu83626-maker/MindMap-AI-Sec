<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  type AgentArtifact,
  type Assignment,
  type FeishuHealthResponse,
  type LlmProvider,
  type ManualAssignmentPayload,
  type ModelSettings,
  type NetworkListenerEvent,
  type NetworkListenerStatus,
  type NetworkMeasureReport,
  type SkillDefinition,
  type SkillExecutionRecord,
  clearNetworkListenerEvents,
  createAssignment,
  deleteAssignment,
  exportNetworkComparisonReport,
  downloadSkill,
  exportNetworkListenerEvents,
  exportNetworkReport,
  fetchAgentEvents,
  fetchAssignments,
  fetchFeishuHealth,
  fetchLlmProviders,
  fetchLlmSettings,
  fetchNetworkListenerEvents,
  fetchNetworkListenerStatus,
  fetchNetworkReports,
  fetchSkillExecutions,
  fetchSkillInvocationHint,
  fetchSkillMarketplace,
  fetchSkills,
  importSkill,
  measureNetworkTarget,
  runAgent,
  saveLlmSettings,
  sendFeishuReminder,
  syncRainClassroom,
  testLlmSettings,
} from './api'

type Page = 'chat' | 'assignments' | 'skills' | 'network' | 'settings' | 'feishu'
type Message = { role: 'user' | 'assistant'; content: string; artifacts?: AgentArtifact[] }

const STORAGE_KEY = 'netscope-model-settings'
const mascotSrc = '/netscope-mascot.gif'
const mascotAvailable = ref(true)
const defaultSettings: ModelSettings = {
  provider: 'custom',
  baseUrl: 'http://117.145.189.131:48081',
  apiKey: '',
  model: 'deepseek',
  apiPath: '/api/v1/chat/completions',
  authType: 'auto',
}

const activePage = ref<Page>('network')
const assignments = ref<Assignment[]>([])
const messages = ref<Message[]>([
  { role: 'assistant', content: '你好，我是 NetScope AI 网络 Agent。你可以让我检测站点安全、测量 DNS/TCP/TLS/TTFB、开启 Web 请求监听，并导出 Word/Excel 分析报告。' },
])
const input = ref('检测 https://example.com 的 HTTPS、TLS 证书、安全响应头和网络测量指标，并生成分析建议')
const loading = ref(false)
const syncing = ref(false)
const notice = ref('')
const assignmentSource = ref('demo')
const assignmentStatus = ref('demo')
const assignmentSyncedAt = ref('')
const activeTool = ref('network_security_measure')
const activeSkillName = ref('network_security_measure')
const agentAutoMode = ref(true)
const llmProviders = ref<LlmProvider[]>([])
const skills = ref<SkillDefinition[]>([])
const settings = ref<ModelSettings>({ ...defaultSettings })
const settingsTesting = ref(false)
const settingsStatus = ref('')
const backendKeyPreview = ref('')
const seenAgentEventIds = ref<Set<string>>(new Set())
const feishuEvents = ref<Message[]>([])
const feishuHealth = ref<FeishuHealthResponse | null>(null)
const skillSourceUrl = ref('')
const skillJson = ref(
  JSON.stringify(
    {
      name: 'security_review_helper',
      title: '安全复盘助手',
      description: '辅助整理信息安全课程中的威胁、资产、控制措施和复盘清单。',
      triggerWords: ['安全', '威胁', '复盘', '控制措施'],
      enabled: true,
    },
    null,
    2,
  ),
)
const skillSignature = ref('')
const skillBusy = ref(false)
const marketplaceSkills = ref<SkillDefinition[]>([])
const skillExecutions = ref<SkillExecutionRecord[]>([])
const skillParameters = ref<Record<string, string>>({})
const networkTarget = ref('https://example.com')
const networkLoading = ref(false)
const networkReport = ref<NetworkMeasureReport | null>(null)
const networkReports = ref<NetworkMeasureReport[]>([])
const networkAgentCommand = ref('检测 https://example.com 的 HTTPS、TLS 证书、安全响应头和网络测量指标')
const networkAgentLoading = ref(false)
const networkAgentAnswer = ref('')
const listenerStatus = ref<NetworkListenerStatus | null>(null)
const listenerEvents = ref<NetworkListenerEvent[]>([])
const listenerLoading = ref(false)

// 对标比较功能
const compareTarget = ref('')
const compareReport = ref<NetworkMeasureReport | null>(null)
const compareLoading = ref(false)

// 实时流程可视化
const measurementProgress = ref<'idle' | 'dns' | 'tcp' | 'tls' | 'http' | 'done'>('idle')
const flowPlaybackIndex = ref(-1)
const flowPlaybackPaused = ref(false)
let flowPlaybackTimer: ReturnType<typeof window.setInterval> | null = null

const form = ref<ManualAssignmentPayload>({
  course: '',
  title: '',
  status: '待完成',
  deadline: '',
  timed: false,
  timeLimitMinutes: 60,
  note: '',
})

const pages: Array<{ key: Page; label: string; icon: string }> = [
  { key: 'network', label: '网络工作台', icon: '#' },
  { key: 'chat', label: 'AI 调度', icon: '>' },
  { key: 'skills', label: 'Skill 工具', icon: '*' },
  { key: 'settings', label: '模型设置', icon: '~' },
  { key: 'feishu', label: '机器人控制', icon: '@' },
]

const providerOptions = computed(() => [
  {
    provider: 'custom',
    displayName: '自定义 OpenAI 兼容接口',
    defaultModel: settings.value.model,
    configured: !!settings.value.apiKey,
  },
  ...llmProviders.value,
])
const nextAssignment = computed(() => assignments.value[0])
const activeSkill = computed(() => skills.value.find((skill) => skill.name === activeSkillName.value))
const enabledSkillCount = computed(() => skills.value.filter((skill) => skill.enabled).length)
const agentModeLabel = computed(() => agentAutoMode.value ? '自动调度' : `锁定 ${activeSkill.value?.title || 'Skill'}`)
const feishuReady = computed(() => feishuHealth.value?.botCredentialsReady ?? false)
const feishuReplyMode = computed(() => feishuHealth.value?.replyMode ?? 'preview-only')
const feishuTips = computed(() => feishuHealth.value?.recommendedNextFeatures || [
  '先配置 FEISHU_APP_ID / FEISHU_APP_SECRET，开启真正的自动回复。',
  '在飞书开放平台验证 /api/feishu/events 回调。',
  '把高频指令做成快捷卡片，减少手输命令。',
])
const marketplaceWithState = computed(() =>
  marketplaceSkills.value.map((skill) => ({
    ...skill,
    installed: skills.value.some((item) => item.name === skill.name),
  })),
)
const sourceLabel = computed(() => {
  if (assignmentSource.value === 'manual') return '手动录入'
  if (assignmentSource.value === 'rain-classroom') return '真实雨课堂'
  return '演示数据'
})
const currentScore = computed(() => (networkReport.value ? calculateSecurityScore(networkReport.value) : 0))
const compareScore = computed(() => (compareReport.value ? calculateSecurityScore(compareReport.value) : 0))
const headerCoverage = computed(() => {
  if (!networkReport.value?.securityHeaders.length) return 0
  return Math.round((networkReport.value.securityHeaders.filter((header) => header.present).length / networkReport.value.securityHeaders.length) * 100)
})
const flowSteps = computed(() => {
  const report = networkReport.value
  return [
    { key: 'client', title: '你的电脑', label: '起点', ms: 0, icon: 'PC' },
    { key: 'dns', title: 'DNS', label: '解析域名', ms: report?.dnsMs ?? 0, icon: 'DNS' },
    { key: 'tcp', title: 'TCP', label: '建立连接', ms: report?.tcpMs ?? 0, icon: 'TCP' },
    { key: 'tls', title: 'TLS', label: '证书握手', ms: report?.tlsMs ?? 0, icon: 'TLS' },
    { key: 'http', title: report?.host || '目标网站', label: '首字节响应', ms: report?.ttfbMs ?? 0, icon: 'WEB' },
  ]
})
const bottleneckKey = computed(() => {
  const candidates = flowSteps.value.filter((step) => step.key !== 'client')
  return candidates.reduce((winner, step) => (step.ms > winner.ms ? step : winner), candidates[0])?.key || ''
})

function formatDeadline(deadline: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(deadline))
}

function toLocalInputValue(deadline: string) {
  const date = new Date(deadline)
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function loadSettings() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) settings.value = { ...defaultSettings, ...JSON.parse(saved) }
  } catch {
    settings.value = { ...defaultSettings }
  }
}

function saveSettings(showNotice = true) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...settings.value, apiKey: '' }))
  if (showNotice) notice.value = '模型设置已保存。'
}

async function loadRuntimeSettings() {
  try {
    const runtime = await fetchLlmSettings()
    settings.value = {
      ...settings.value,
      ...runtime,
      apiKey: '',
    }
    backendKeyPreview.value = runtime.apiKeyPreview || ''
    settingsStatus.value = runtime.configured
      ? `后端已接入：${runtime.resolvedUrl || runtime.baseUrl} / Key ${runtime.apiKeyPreview || '已保存'}`
      : '后端还没有保存 API Key'
  } catch {
    settingsStatus.value = '后端模型配置暂时不可读取'
  }
}

async function saveRuntimeSettings(showNotice = true) {
  inferSettingsFromKey()
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...settings.value, apiKey: '' }))
  try {
    const saved = await saveLlmSettings(settings.value)
    settings.value = {
      ...settings.value,
      ...saved,
      apiKey: '',
    }
    backendKeyPreview.value = saved.apiKeyPreview || ''
    settingsStatus.value = `已保存到后端：${saved.resolvedUrl || saved.baseUrl} / Key ${saved.apiKeyPreview || '已保存'}`
    if (showNotice) notice.value = '模型 API 已保存，网页和飞书 Agent 会共用这套配置。'
  } catch (error) {
    settingsStatus.value = error instanceof Error ? error.message : '保存后端模型配置失败'
    if (showNotice) notice.value = settingsStatus.value
  }
}

function inferSettingsFromKey() {
  const key = settings.value.apiKey?.trim()
  const hasBaseUrl = !!settings.value.baseUrl?.trim()
  if (!key || hasBaseUrl) return
  settings.value = {
    ...settings.value,
    provider: 'custom',
    baseUrl: 'https://api.deepseek.com',
    apiPath: '/chat/completions',
    model: settings.value.model || 'deepseek-v4-flash',
    authType: 'bearer',
  }
  settingsStatus.value = '已根据 API Key 自动使用 DeepSeek 官方配置。'
}

async function testRuntimeSettings() {
  inferSettingsFromKey()
  settingsTesting.value = true
  settingsStatus.value = '正在测试模型连接...'
  try {
    const result = await testLlmSettings(settings.value)
    settingsStatus.value = `连接成功：${result.message}`
    notice.value = `模型测试通过：${result.resolvedUrl}`
  } catch (error) {
    settingsStatus.value = error instanceof Error ? error.message : '模型测试失败'
    notice.value = settingsStatus.value
  } finally {
    settingsTesting.value = false
  }
}

function useDeepSeekOfficialPreset() {
  settings.value = {
    ...settings.value,
    provider: 'custom',
    baseUrl: 'https://api.deepseek.com',
    apiPath: '/chat/completions',
    model: 'deepseek-v4-flash',
    authType: 'bearer',
  }
  settingsStatus.value = '已切换为 DeepSeek 官方 API 预设，请填入官方 API Key 后保存并测试。'
}

function useSchoolGatewayPreset() {
  settings.value = {
    ...settings.value,
    provider: 'custom',
    baseUrl: 'http://117.145.189.131:48081',
    apiPath: '/api/v1/chat/completions',
    model: 'deepseek',
    authType: 'auto',
  }
  settingsStatus.value = '已切换为学校本地网关预设。'
}

function clearModelUrlForAuto() {
  settings.value = {
    ...settings.value,
    baseUrl: '',
    apiPath: 'auto',
    authType: 'auto',
  }
  settingsStatus.value = '已清空 Model URL；保存时会根据 API Key 自动选择默认配置。'
}

function resetForm() {
  form.value = {
    course: '',
    title: '',
    status: '待完成',
    deadline: '',
    timed: false,
    timeLimitMinutes: 60,
    note: '',
  }
}

async function loadAssignments() {
  try {
    const response = await fetchAssignments()
    assignments.value = response.assignments
    assignmentSource.value = response.source
    assignmentStatus.value = response.status
    assignmentSyncedAt.value = response.syncedAt
    notice.value = response.message
  } catch {
    notice.value = '读取作业失败，请检查后端服务。'
  }
}

async function submitAssignment() {
  if (!form.value.course.trim() || !form.value.title.trim() || !form.value.deadline) {
    notice.value = '请填写课程名称、作业名称和截止时间。'
    return
  }
  const payload = {
    ...form.value,
    deadline: new Date(form.value.deadline).toISOString(),
  }
  try {
    await createAssignment(payload)
    notice.value = '作业已录入。'
    resetForm()
    await loadAssignments()
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '录入失败。'
  }
}

function editAssignment(assignment: Assignment) {
  form.value = {
    course: assignment.course,
    title: assignment.title,
    status: assignment.status || '待完成',
    deadline: toLocalInputValue(assignment.deadline),
    timed: assignment.timed,
    timeLimitMinutes: assignment.timeLimitMinutes || 60,
    note: assignment.note || '',
  }
}

async function removeAssignment(assignment: Assignment) {
  if (!assignment.id) return
  try {
    await deleteAssignment(assignment.id)
    notice.value = '作业已删除。'
    await loadAssignments()
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '删除失败。'
  }
}

async function loadLlmProviders() {
  try {
    llmProviders.value = await fetchLlmProviders()
  } catch {
    llmProviders.value = [
      { provider: 'openai', displayName: 'OpenAI', defaultModel: 'gpt-4o-mini', configured: false },
      { provider: 'kimi', displayName: 'Kimi', defaultModel: 'moonshot-v1-8k', configured: false },
      { provider: 'doubao', displayName: '豆包', defaultModel: '', configured: false },
    ]
  }
}

async function loadSkills() {
  skills.value = await fetchSkills()
  if (!skills.value.some((skill) => skill.name === activeSkillName.value) && skills.value[0]) {
    activeSkillName.value = skills.value[0].name
  }
  syncSkillParameterDefaults()
}

async function loadMarketplace() {
  try {
    marketplaceSkills.value = await fetchSkillMarketplace()
  } catch {
    marketplaceSkills.value = []
  }
}

async function loadFeishuHealth() {
  try {
    feishuHealth.value = await fetchFeishuHealth()
  } catch {
    feishuHealth.value = null
  }
}

async function loadSkillExecutions() {
  try {
    skillExecutions.value = await fetchSkillExecutions('', 30)
  } catch {
    skillExecutions.value = []
  }
}

async function loadNetworkReports() {
  try {
    networkReports.value = await fetchNetworkReports(20)
    if (!networkReport.value && networkReports.value[0]) {
      networkReport.value = networkReports.value[0]
    }
  } catch {
    networkReports.value = []
  }
}

async function loadListenerStatus() {
  try {
    listenerStatus.value = await fetchNetworkListenerStatus()
  } catch {
    listenerStatus.value = null
  }
}

async function loadListenerEvents() {
  try {
    listenerEvents.value = await fetchNetworkListenerEvents(50)
  } catch {
    listenerEvents.value = []
  }
}

async function refreshListener() {
  listenerLoading.value = true
  try {
    await Promise.all([loadListenerStatus(), loadListenerEvents()])
    notice.value = '监听日志已刷新。'
  } finally {
    listenerLoading.value = false
  }
}

async function clearListener() {
  listenerLoading.value = true
  try {
    await clearNetworkListenerEvents()
    await refreshListener()
    notice.value = '监听日志已清空。'
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '清空监听日志失败'
  } finally {
    listenerLoading.value = false
  }
}

async function exportReport(format: 'docx' | 'xlsx') {
  if (!networkReport.value) return
  try {
    const artifact = await exportNetworkReport(networkReport.value.id, format)
    messages.value.push({ role: 'assistant', content: `已生成网络测量报告：${artifact.filename}`, artifacts: [artifact] })
    notice.value = `已生成 ${format.toUpperCase()} 报告。`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '导出报告失败'
  }
}

async function exportListener(format: 'docx' | 'xlsx') {
  try {
    const artifact = await exportNetworkListenerEvents(format)
    messages.value.push({ role: 'assistant', content: `已生成 Web 监听日志：${artifact.filename}`, artifacts: [artifact] })
    notice.value = `已生成监听 ${format.toUpperCase()} 文件。`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '导出监听日志失败'
  }
}

async function copyListenerUrl() {
  const url = listenerStatus.value?.captureUrl || 'http://localhost:8090/api/network/listener/capture'
  try {
    await navigator.clipboard.writeText(url)
    notice.value = '监听地址已复制。'
  } catch {
    notice.value = url
  }
}

function stopFlowPlayback() {
  if (flowPlaybackTimer) {
    window.clearInterval(flowPlaybackTimer)
    flowPlaybackTimer = null
  }
}

function startFlowPlayback() {
  stopFlowPlayback()
  flowPlaybackPaused.value = false
  flowPlaybackIndex.value = 0
  measurementProgress.value = 'dns'
  flowPlaybackTimer = window.setInterval(() => {
    if (flowPlaybackPaused.value) return
    if (flowPlaybackIndex.value >= flowSteps.value.length - 1) {
      measurementProgress.value = 'done'
      stopFlowPlayback()
      return
    }
    flowPlaybackIndex.value += 1
    const active = flowSteps.value[flowPlaybackIndex.value]?.key
    measurementProgress.value = active === 'client' ? 'dns' : active === 'http' ? 'http' : (active as typeof measurementProgress.value)
  }, 750)
}

function toggleFlowPlayback() {
  if (!networkReport.value) return
  if (!flowPlaybackTimer && flowPlaybackIndex.value >= flowSteps.value.length - 1) {
    startFlowPlayback()
    return
  }
  flowPlaybackPaused.value = !flowPlaybackPaused.value
}

function replayFlow() {
  if (!networkReport.value) return
  startFlowPlayback()
}

async function handleNetworkMeasure() {
  const target = networkTarget.value.trim()
  if (!target || networkLoading.value) return
  networkLoading.value = true
  measurementProgress.value = 'dns'
  notice.value = '正在执行网络安全与网络测量检测...'
  try {
    const report = await measureNetworkTarget(target)
    networkReport.value = report
    startFlowPlayback()
    await loadNetworkReports()
    notice.value = `检测完成：${report.target}，风险等级 ${report.riskLevel}`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '网络检测失败'
    measurementProgress.value = 'idle'
    stopFlowPlayback()
  } finally {
    networkLoading.value = false
  }
}

function calculateSecurityScore(report: NetworkMeasureReport): number {
  let score = 100

  // HTTPS 检查 (20分)
  if (!report.httpsEnabled) {
    score -= 20
  }

  // 证书有效性 (20分)
  if (!report.certificateValid) {
    score -= 20
  } else if (report.certificateDaysRemaining !== undefined && report.certificateDaysRemaining < 30) {
    score -= 5
  }

  // 安全响应头 (30分，每缺一个关键头-5分)
  const criticalHeaders = ['Content-Security-Policy', 'Strict-Transport-Security', 'X-Frame-Options', 'X-Content-Type-Options']
  const missingCritical = report.securityHeaders.filter(h => criticalHeaders.includes(h.name) && !h.present).length
  score -= missingCritical * 5

  // HTTP 状态码 (15分)
  if (!report.httpStatus || report.httpStatus >= 500) {
    score -= 15
  } else if (report.httpStatus >= 400) {
    score -= 8
  }

  // 网络性能 (15分)
  if (report.totalMs && report.totalMs > 5000) {
    score -= 10
  } else if (report.totalMs && report.totalMs > 2000) {
    score -= 5
  }

  return Math.max(0, score)
}

function getScoreGrade(score: number): string {
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  if (score >= 60) return 'D'
  return 'F'
}

function getScoreColor(score: number): string {
  if (score >= 80) return '#22c55e'
  if (score >= 60) return '#f59e0b'
  return '#ef4444'
}

async function handleCompare() {
  const target = compareTarget.value.trim()
  if (!target || compareLoading.value) return
  compareLoading.value = true
  try {
    const report = await measureNetworkTarget(target)
    compareReport.value = report
    notice.value = `已加载对标网站：${report.target}`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '对标网站检测失败'
  } finally {
    compareLoading.value = false
  }
}

async function exportComparison(format: 'docx' | 'xlsx') {
  if (!networkReport.value || !compareReport.value) return
  try {
    const artifact = await exportNetworkComparisonReport(networkReport.value.id, compareReport.value.id, format)
    messages.value.push({ role: 'assistant', content: `已生成网站对标报告：${artifact.filename}`, artifacts: [artifact] })
    notice.value = `已生成对标 ${format.toUpperCase()} 报告。`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '导出对标报告失败'
  }
}

function clearCompare() {
  compareTarget.value = ''
  compareReport.value = null
}

function formatMetric(value?: number) {
  return value === undefined || value === null ? 'n/a' : `${value} ms`
}

async function handleNetworkAgent() {
  const command = networkAgentCommand.value.trim()
  if (!command || networkAgentLoading.value) return
  networkAgentLoading.value = true
  networkAgentAnswer.value = ''
  notice.value = '网页 Agent 正在编排网络安全测量任务...'
  try {
    await saveRuntimeSettings(false)
    const response = await runAgent(command, settings.value, 'network_security_measure', {
      url: networkTarget.value,
      mode: '综合检测',
    })
    networkAgentAnswer.value = response.answer
    if (response.data.networkReport) {
      networkReport.value = response.data.networkReport
      await loadNetworkReports()
    }
    messages.value.push({ role: 'user', content: `网页网络 Agent：${command}` })
    messages.value.push({ role: 'assistant', content: response.answer, artifacts: response.data.artifacts || [] })
    notice.value = '网页 Agent 调用完成，结果已同步到任务工作台。'
  } catch (error) {
    networkAgentAnswer.value = error instanceof Error ? error.message : 'Agent 调用失败'
    notice.value = networkAgentAnswer.value
  } finally {
    networkAgentLoading.value = false
  }
}

function applyAssignmentsFromData(data: { assignments?: Assignment[]; relatedAssignments?: Assignment[] }) {
  const nextAssignments = data.assignments || data.relatedAssignments || []
  if (nextAssignments.length) assignments.value = nextAssignments
}

async function useTool(skill: SkillDefinition) {
  activeTool.value = skill.name
  activeSkillName.value = skill.name
  agentAutoMode.value = false
  syncSkillParameterDefaults(skill)
  try {
    const hint = await fetchSkillInvocationHint(skill.name)
    input.value = hint.prompt
  } catch {
    input.value = `请调用 ${skill.title} skill，并把结果整理成可执行的下一步。`
  }
  activePage.value = 'chat'
}

function enableAgentAutoMode() {
  agentAutoMode.value = true
  activeSkillName.value = 'network_security_measure'
  input.value = '检测 https://example.com 的 HTTPS、TLS 证书、安全响应头和网络测量指标，并给出修复建议'
}

function syncSkillParameterDefaults(skill = activeSkill.value) {
  if (!skill) return
  const nextValues: Record<string, string> = {}
  for (const parameter of skill.parameters || []) {
    nextValues[parameter.name] =
      skillParameters.value[parameter.name] || parameter.defaultValue || parameter.options?.[0] || ''
  }
  if (skill.name === 'network_security_measure' && !nextValues.url) {
    nextValues.url = networkTarget.value || 'https://example.com'
  }
  skillParameters.value = nextValues
}

async function handleSend() {
  const text = input.value.trim()
  if (!text || loading.value) return

  await saveRuntimeSettings(false)
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true

  try {
    const response = await runAgent(
      text,
      settings.value,
      agentAutoMode.value ? undefined : activeSkillName.value,
      agentAutoMode.value ? {} : skillParameters.value,
    )
    messages.value.push({ role: 'assistant', content: response.answer, artifacts: response.data.artifacts || [] })
    if (response.data.networkReport) {
      networkReport.value = response.data.networkReport
      await loadNetworkReports()
    }
    await loadListenerEvents()
    loadSkillExecutions()
  } catch (error) {
    messages.value.push({
      role: 'assistant',
      content: error instanceof Error ? error.message : '聊天服务暂时不可用。',
    })
  } finally {
    loading.value = false
  }
}

async function handleSync() {
  syncing.value = true
  try {
    const response = await syncRainClassroom()
    assignments.value = response.assignments
    assignmentSource.value = response.source
    assignmentStatus.value = response.status
    assignmentSyncedAt.value = response.syncedAt
    notice.value = response.message
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '同步失败。'
  } finally {
    syncing.value = false
  }
}

async function handleReminder(assignment: Assignment) {
  activeTool.value = 'feishu_notify'
  activeSkillName.value = 'feishu_notify'
  try {
    const response = await sendFeishuReminder(assignment.id)
    notice.value = response.preview
    activePage.value = 'feishu'
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '飞书提醒生成失败。'
  }
}

async function handleImportSkill() {
  if (!skillJson.value.trim() || skillBusy.value) return
  skillBusy.value = true
  try {
    const skill = await importSkill(skillJson.value, skillSignature.value)
    notice.value = `已装载 Skill：${skill.title}`
    await loadSkills()
    await useTool(skill)
  } catch (error) {
    notice.value = error instanceof Error ? error.message : 'Skill 导入失败。'
  } finally {
    skillBusy.value = false
  }
}

async function installMarketplaceSkill(skill: SkillDefinition) {
  skillBusy.value = true
  try {
    const installed = await importSkill(JSON.stringify(skill), skill.signature || '')
    notice.value = `已安装：${installed.title}`
    await loadSkills()
    await useTool(installed)
  } catch (error) {
    notice.value = error instanceof Error ? error.message : 'Skill 安装失败。'
  } finally {
    skillBusy.value = false
  }
}

async function handleDownloadSkill() {
  if (!skillSourceUrl.value.trim() || skillBusy.value) return
  skillBusy.value = true
  try {
    const skill = await downloadSkill(skillSourceUrl.value.trim())
    notice.value = `已装载 Skill：${skill.title}`
    await loadSkills()
    await useTool(skill)
  } catch (error) {
    notice.value = error instanceof Error ? error.message : 'Skill 下载失败。'
  } finally {
    skillBusy.value = false
  }
}

async function pollFeishuEvents() {
  try {
    const events = await fetchAgentEvents('feishu', 20)
    const nextLog: Message[] = []
    for (const event of [...events].reverse()) {
      nextLog.push({ role: 'user', content: `飞书指令：${event.command}` })
      nextLog.push({ role: 'assistant', content: event.answer })
      if (seenAgentEventIds.value.has(event.id)) continue
      seenAgentEventIds.value.add(event.id)
      messages.value.push({ role: 'user', content: `飞书指令：${event.command}` })
      messages.value.push({ role: 'assistant', content: event.answer })
    }
    feishuEvents.value = nextLog
  } catch {
    // Event feed can be unavailable while chat still works.
  }
}

watch(
  settings,
  () => saveSettings(false),
  { deep: true },
)

watch(activeSkillName, () => syncSkillParameterDefaults())

watch(activePage, (page) => {
  if (page === 'feishu') {
    loadFeishuHealth()
    pollFeishuEvents()
  }
  if (page === 'network') {
    loadNetworkReports()
    loadListenerStatus()
    loadListenerEvents()
  }
})

onMounted(() => {
  loadSettings()
  loadRuntimeSettings()
  loadLlmProviders()
  loadSkills()
  loadMarketplace()
  loadSkillExecutions()
  loadNetworkReports()
  loadListenerStatus()
  loadListenerEvents()
  loadFeishuHealth()
  pollFeishuEvents()
  window.setInterval(pollFeishuEvents, 3000)
  window.setInterval(loadFeishuHealth, 15000)
})

onUnmounted(() => {
  stopFlowPlayback()
})
</script>

<template>
  <main class="app-shell">
    <aside class="app-sidebar">
      <header class="product-head">
        <div class="brand-mark">M</div>
        <div>
          <p class="eyebrow">NetScope AI</p>
          <h1>网络安全中枢</h1>
        </div>
      </header>

      <nav class="page-nav" aria-label="Primary">
        <button
          v-for="page in pages"
          :key="page.key"
          :class="{ active: activePage === page.key }"
          @click="activePage = page.key"
        >
          <span>{{ page.icon }}</span>
          {{ page.label }}
        </button>
      </nav>

      <section class="status-board compact">
        <p class="eyebrow">当前目标</p>
        <h2>{{ networkReport?.host || networkTarget }}</h2>
        <p>{{ networkReport ? `风险等级 ${networkReport.riskLevel}` : '等待网络安全测量' }}</p>
        <time v-if="networkReport">{{ formatDeadline(networkReport.checkedAt) }}</time>
      </section>

      <section class="source-board">
        <p class="eyebrow">Web 监听</p>
        <strong :class="{ real: listenerStatus?.enabled }">
          {{ listenerStatus?.enabled ? '已启用' : '未连接' }}
        </strong>
        <span>{{ listenerEvents.length }} 条请求日志</span>
        <time v-if="listenerStatus?.lastCapturedAt">{{ formatDeadline(listenerStatus.lastCapturedAt) }}</time>
      </section>

      <figure class="sidebar-mascot" aria-label="NetScope AI mascot">
        <img
          v-if="mascotAvailable"
          :src="mascotSrc"
          alt="NetScope AI mascot"
          @error="mascotAvailable = false"
        />
        <figcaption v-else>
          将 GIF 放到 frontend/public/netscope-mascot.gif
        </figcaption>
      </figure>
    </aside>

    <section class="app-content">
      <section v-if="false && activePage === 'assignments'" class="page">
        <header class="page-head">
          <div>
            <p class="eyebrow">Manual Assignments</p>
            <h2>作业录入台</h2>
          </div>
          <div class="head-actions">
            <span class="source-chip" :class="{ real: assignmentSource === 'manual' || assignmentSource === 'rain-classroom' }">
              {{ sourceLabel }}
            </span>
            <button class="primary-button" :disabled="syncing" @click="handleSync">
              {{ syncing ? '读取中' : '刷新' }}
            </button>
          </div>
        </header>

        <div class="assignment-workbench">
          <form class="assignment-form" @submit.prevent="submitAssignment">
            <label>
              <span>课程名称</span>
              <input v-model="form.course" placeholder="例如：信息安全导论" />
            </label>
            <label>
              <span>作业名称</span>
              <input v-model="form.title" placeholder="例如：访问控制模型思维导图" />
            </label>
            <label>
              <span>截止时间</span>
              <input v-model="form.deadline" type="datetime-local" />
            </label>
            <label>
              <span>状态</span>
              <select v-model="form.status">
                <option>待完成</option>
                <option>进行中</option>
                <option>待检查</option>
                <option>已完成</option>
              </select>
            </label>
            <label class="toggle-row">
              <input v-model="form.timed" type="checkbox" />
              <span>这是限时作业/测试</span>
            </label>
            <label>
              <span>限时分钟</span>
              <input v-model.number="form.timeLimitMinutes" type="number" min="1" :disabled="!form.timed" />
            </label>
            <label class="wide-field">
              <span>备注</span>
              <textarea v-model="form.note" rows="4" placeholder="评分要求、提交入口、材料位置等" />
            </label>
            <div class="form-actions">
              <button class="send-button" type="submit">保存作业</button>
              <button class="ghost-button" type="button" @click="resetForm">清空</button>
            </div>
          </form>

          <div class="assignment-grid">
            <article v-for="assignment in assignments" :key="assignment.id" class="assignment-card">
              <div>
                <p class="eyebrow">{{ assignment.platform }} / {{ assignment.status }}</p>
                <h3>{{ assignment.course }}</h3>
                <p>{{ assignment.title }}</p>
                <small v-if="assignment.note">{{ assignment.note }}</small>
              </div>
              <footer>
                <div>
                  <time>{{ formatDeadline(assignment.deadline) }}</time>
                  <span v-if="assignment.timed" class="limit-chip">限时 {{ assignment.timeLimitMinutes }} 分钟</span>
                </div>
                <div class="card-actions">
                  <button @click="editAssignment(assignment)">编辑</button>
                  <button @click="handleReminder(assignment)">飞书</button>
                  <button class="danger-button" @click="removeAssignment(assignment)">删除</button>
                </div>
              </footer>
            </article>
          </div>
        </div>
      </section>

      <section v-else-if="activePage === 'chat'" class="workspace">
        <section class="page page-chat">
          <header class="page-head">
            <div>
              <p class="eyebrow">Agent Console</p>
              <h2>OpenClaw 式 Agent 工作台</h2>
            </div>
            <div class="model-pill">
              <span>{{ settings.provider }}</span>
              <strong>{{ agentModeLabel }}</strong>
            </div>
          </header>

          <div class="message-list" aria-live="polite">
            <article v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
              <span class="avatar">{{ message.role === 'user' ? '我' : 'AI' }}</span>
              <div class="message-body">
                <p>{{ message.content }}</p>
                <div v-if="message.artifacts?.length" class="artifact-list">
                  <a
                    v-for="artifact in message.artifacts"
                    :key="artifact.id"
                    class="artifact-link"
                    :href="artifact.url"
                    target="_blank"
                    rel="noreferrer"
                  >
                    下载 {{ artifact.filename }}
                  </a>
                </div>
              </div>
            </article>
          </div>

          <form class="composer composer-agent" @submit.prevent="handleSend">
            <div class="prompt-bar">
              <button type="button" @click="input = '检测 https://example.com 的 HTTPS、TLS 证书、安全响应头和 DNS/TCP/TLS/TTFB 指标'">检测网站</button>
              <button type="button" @click="input = '开启 Web 请求监听，分析最近请求日志并导出 Excel 表格'">请求监听</button>
              <button type="button" @click="input = '把当前网络检测结果整理成 Word 安全分析报告'">生成报告</button>
            </div>
            <textarea v-model="input" rows="3" placeholder="例如：检测网站安全、开启 Web 监听、生成 Word/Excel 分析报告" />
            <button class="send-button" type="submit" :disabled="loading">
              {{ loading ? '执行中' : '发送' }}
            </button>
          </form>
        </section>

        <aside class="side-panel">
          <section class="panel-block">
            <p class="eyebrow">当前 Skill</p>
            <h3>{{ activeSkill?.title || '暂无 Skill' }}</h3>
            <p>{{ activeSkill?.description || '等待选择 Skill。' }}</p>
          </section>

          <section class="panel-block">
            <p class="eyebrow">Agent Mode</p>
            <h3>{{ agentModeLabel }}</h3>
            <p v-if="agentAutoMode">默认由 Agent 自主判断并调用网络安全测量、Web 请求监听、报告生成、模型分析或机器人通知。</p>
            <p v-else>当前锁定：{{ activeSkill?.title || 'Skill' }}。发送消息时会优先调用这个工具。</p>
            <div class="agent-mode-actions">
              <button class="primary-button" :disabled="agentAutoMode" @click="enableAgentAutoMode">自动调度</button>
              <button class="ghost-button" :disabled="!activeSkillName" @click="agentAutoMode = false">锁定当前</button>
            </div>
          </section>

          <section v-if="!agentAutoMode && activeSkill?.parameters?.length" class="panel-block">
            <p class="eyebrow">Parameters</p>
            <label v-for="parameter in activeSkill.parameters" :key="parameter.name" class="param-field">
              <span>{{ parameter.label }}{{ parameter.required ? ' *' : '' }}</span>
              <select v-if="parameter.type === 'select'" v-model="skillParameters[parameter.name]">
                <option v-for="option in parameter.options || []" :key="option" :value="option">{{ option }}</option>
              </select>
              <input
                v-else
                v-model="skillParameters[parameter.name]"
                :type="parameter.type === 'number' ? 'number' : 'text'"
                :placeholder="parameter.placeholder || parameter.defaultValue || ''"
              />
            </label>
          </section>

          <section class="panel-block">
            <p class="eyebrow">Skills</p>
            <div class="mini-skills">
              <button
                v-for="skill in skills.slice(0, 5)"
                :key="skill.name"
                :class="{ active: !agentAutoMode && activeSkillName === skill.name }"
                @click="useTool(skill)"
              >
                {{ skill.title }}
              </button>
            </div>
          </section>
        </aside>
      </section>

      <section v-else-if="activePage === 'skills'" class="page skills-page">
        <header class="page-head">
          <div>
            <p class="eyebrow">Skills / {{ enabledSkillCount }} enabled</p>
            <h2>Skill 工具</h2>
          </div>
        </header>

        <div class="skill-layout">
          <section class="skill-stack">
            <section>
              <h3>Skill 市场</h3>
              <div class="tool-grid">
                <article v-for="skill in marketplaceWithState" :key="skill.name" class="tool-card market-card">
                  <span class="skill-dot"></span>
                  <strong>{{ skill.title }}</strong>
                  <small>{{ skill.description }}</small>
                  <em>{{ skill.triggerWords.slice(0, 4).join(' / ') }}</em>
                  <button
                    class="primary-button"
                    :disabled="skillBusy || skill.installed"
                    @click="installMarketplaceSkill(skill)"
                  >
                    {{ skill.installed ? '已安装' : '安装' }}
                  </button>
                </article>
              </div>
            </section>

            <section>
              <h3>本地 Skills</h3>
              <div class="tool-grid">
                <button
                  v-for="skill in skills"
                  :key="skill.name"
                  class="tool-card"
                  :class="{ active: activeTool === skill.name }"
                  @click="useTool(skill)"
                >
                  <span class="skill-dot"></span>
                  <strong>{{ skill.title }}</strong>
                  <small>{{ skill.description }}</small>
                  <em>{{ skill.triggerWords.slice(0, 4).join(' / ') }}</em>
                </button>
              </div>
            </section>

            <section>
              <h3>执行日志</h3>
              <div class="execution-list">
                <article v-for="record in skillExecutions" :key="record.id" class="execution-item">
                  <div>
                    <strong>{{ record.skillTitle }}</strong>
                    <span>{{ record.status }} / {{ record.durationMs }}ms</span>
                  </div>
                  <p>{{ record.summary }}</p>
                </article>
              </div>
            </section>
          </section>

          <aside class="import-panel">
            <label>
              <span>Skill JSON URL</span>
              <input v-model="skillSourceUrl" placeholder="https://example.com/skill.json" />
            </label>
            <button class="primary-button" :disabled="skillBusy || !skillSourceUrl.trim()" @click="handleDownloadSkill">
              下载 Skill
            </button>

            <label>
              <span>签名</span>
              <input v-model="skillSignature" placeholder="HMAC-SHA256，可选" />
            </label>

            <label>
              <span>Skill JSON</span>
              <textarea v-model="skillJson" rows="12" />
            </label>
            <button class="send-button" :disabled="skillBusy || !skillJson.trim()" @click="handleImportSkill">
              导入 Skill
            </button>
          </aside>
        </div>
      </section>

      <section v-else-if="activePage === 'network'" class="page network-page">
        <header class="page-head">
          <div>
            <p class="eyebrow">Network Security / Measurement</p>
            <h2>网络安全测量中心</h2>
          </div>
          <button class="primary-button" :disabled="networkLoading" @click="handleNetworkMeasure">
            {{ networkLoading ? '检测中' : '开始检测' }}
          </button>
        </header>

        <section class="network-feature-grid">
          <article>
            <p class="eyebrow">Network Security</p>
            <h3>HTTPS / TLS / 安全头</h3>
            <p>检查证书有效期、HSTS、CSP、点击劫持防护、MIME sniffing 等 Web 暴露面。</p>
          </article>
          <article>
            <p class="eyebrow">Network Measurement</p>
            <h3>DNS / TCP / TTFB</h3>
            <p>把解析、建连、握手、首字节和总耗时拆开，方便定位是网络慢还是应用慢。</p>
          </article>
          <article>
            <p class="eyebrow">Web Application</p>
            <h3>请求监听与日志复盘</h3>
            <p>接收 Webhook/测试请求，记录 Header、Body 预览、User-Agent 和请求级风险提示。</p>
          </article>
        </section>

        <section class="network-input-row network-direct-card">
          <label>
            <span>目标 URL</span>
            <input v-model="networkTarget" placeholder="https://example.com" @keyup.enter="handleNetworkMeasure" />
          </label>
          <div class="network-mode-list">
            <span>HTTPS/TLS</span>
            <span>安全响应头</span>
            <span>DNS/TCP/TLS/TTFB</span>
            <span>可用性</span>
          </div>
        </section>

        <section class="network-agent-card">
          <div>
            <p class="eyebrow">Web Agent</p>
            <h3>网页 Agent 调用</h3>
            <p>用自然语言调用内置 network_security_measure skill，结果会同步到报告区和任务工作台。</p>
          </div>
          <textarea v-model="networkAgentCommand" rows="4" placeholder="例如：检测 https://example.com 的 HTTPS、TLS 证书、安全响应头和网络测量指标" />
          <div class="agent-action-row">
            <button class="send-button" :disabled="networkAgentLoading" @click="handleNetworkAgent">
              {{ networkAgentLoading ? 'Agent 执行中' : '调用 Agent' }}
            </button>
            <button
              class="ghost-button"
              type="button"
              @click="networkAgentCommand = `检测 ${networkTarget} 的 HTTPS、TLS 证书、安全响应头和网络测量指标`"
            >
              套用当前 URL
            </button>
          </div>
          <p v-if="networkAgentAnswer" class="agent-answer">{{ networkAgentAnswer }}</p>
        </section>

        <!-- 对标比较区块 -->
        <section class="network-compare-card">
          <div>
            <p class="eyebrow">网站对标比较</p>
            <h3>选择对标网站进行性能和安全对比</h3>
          </div>
          <div class="compare-input-row">
            <input
              v-model="compareTarget"
              placeholder="输入对标网站 URL (如: https://competitor.com)"
              @keyup.enter="handleCompare"
            />
            <button class="primary-button" :disabled="compareLoading || !compareTarget.trim()" @click="handleCompare">
              {{ compareLoading ? '加载中' : '加载对标网站' }}
            </button>
            <button v-if="compareReport" class="ghost-button" @click="clearCompare">清空对标</button>
          </div>

          <div v-if="networkReport && compareReport" class="comparison-grid">
            <div class="comparison-head">
              <div>
                <strong>{{ networkReport.host }}</strong>
                <span>当前目标</span>
              </div>
              <div class="compare-versus">VS</div>
              <div>
                <strong>{{ compareReport.host }}</strong>
                <span>对标目标</span>
              </div>
            </div>
            <div class="compare-score-row">
              <div class="compare-score" :style="{ '--score-color': getScoreColor(currentScore), '--score-deg': `${currentScore * 3.6}deg` } as any">
                <strong>{{ currentScore }}</strong>
                <span>{{ getScoreGrade(currentScore) }}</span>
              </div>
              <p>
                {{ currentScore >= compareScore ? '当前网站综合表现领先' : '对标网站综合表现领先' }}
                {{ Math.abs(currentScore - compareScore) }} 分
              </p>
              <div class="compare-score" :style="{ '--score-color': getScoreColor(compareScore), '--score-deg': `${compareScore * 3.6}deg` } as any">
                <strong>{{ compareScore }}</strong>
                <span>{{ getScoreGrade(compareScore) }}</span>
              </div>
            </div>
            <div class="comparison-item">
              <span class="label">网站</span>
              <div class="compare-value left">{{ networkReport.host }}</div>
              <div class="compare-value right">{{ compareReport.host }}</div>
            </div>
            <div class="comparison-item">
              <span class="label">安全等级</span>
              <div :class="['compare-value', 'left', `risk-${networkReport.riskLevel}`]">
                {{ networkReport.riskLevel }}
              </div>
              <div :class="['compare-value', 'right', `risk-${compareReport.riskLevel}`]">
                {{ compareReport.riskLevel }}
              </div>
            </div>
            <div class="comparison-item">
              <span class="label">HTTPS</span>
              <div class="compare-value left">
                {{ networkReport.httpsEnabled ? '✅ 已启用' : '❌ 未启用' }}
              </div>
              <div class="compare-value right">
                {{ compareReport.httpsEnabled ? '✅ 已启用' : '❌ 未启用' }}
              </div>
            </div>
            <div class="comparison-item">
              <span class="label">证书状态</span>
              <div class="compare-value left">
                {{ networkReport.certificateValid ? '✅ 有效' : '❌ 异常' }}
              </div>
              <div class="compare-value right">
                {{ compareReport.certificateValid ? '✅ 有效' : '❌ 异常' }}
              </div>
            </div>
            <div class="comparison-item">
              <span class="label">总耗时</span>
              <div class="compare-value left">{{ formatMetric(networkReport.totalMs) }}</div>
              <div class="compare-value right">{{ formatMetric(compareReport.totalMs) }}</div>
            </div>
            <div class="comparison-item">
              <span class="label">DNS</span>
              <div class="compare-value left">{{ formatMetric(networkReport.dnsMs) }}</div>
              <div class="compare-value right">{{ formatMetric(compareReport.dnsMs) }}</div>
            </div>
            <div class="comparison-item">
              <span class="label">安全头覆盖</span>
              <div class="compare-value left">
                {{ networkReport.securityHeaders.filter(h => h.present).length }}/{{ networkReport.securityHeaders.length }}
              </div>
              <div class="compare-value right">
                {{ compareReport.securityHeaders.filter(h => h.present).length }}/{{ compareReport.securityHeaders.length }}
              </div>
            </div>
            <div class="agent-action-row compare-export-row">
              <button class="ghost-button" type="button" @click="exportComparison('docx')">导出对比 Word</button>
              <button class="ghost-button" type="button" @click="exportComparison('xlsx')">导出对比 Excel</button>
            </div>
          </div>
        </section>

        <!-- 实时网络流程可视化 -->
        <section v-if="networkReport" class="network-flow-visualization">
          <div class="flow-header">
            <div>
              <p class="eyebrow">实时网络流程可视化</p>
              <h3>从你的电脑到目标网站的请求路径</h3>
            </div>
            <div class="flow-actions">
              <button class="ghost-button" type="button" @click="toggleFlowPlayback">
                {{ flowPlaybackPaused ? '继续' : '暂停' }}
              </button>
              <button class="primary-button" type="button" @click="replayFlow">回放</button>
            </div>
          </div>

          <div class="flow-diagram">
            <template v-for="(step, index) in flowSteps" :key="step.key">
              <div
                class="flow-step"
                :class="{
                  active: flowPlaybackIndex >= index || measurementProgress === 'done',
                  current: flowPlaybackIndex === index && measurementProgress !== 'done',
                  bottleneck: step.key === bottleneckKey,
                }"
              >
                <div class="step-circle">{{ step.icon }}</div>
                <div class="step-content">
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.key === 'client' ? step.label : formatMetric(step.ms) }}</span>
                </div>
                <div
                  v-if="step.key !== 'client'"
                  class="step-bar"
                  :style="{ width: `${Math.max(10, Math.min(100, (step.ms / Math.max(1, networkReport.totalMs || 1)) * 100))}%` }"
                ></div>
                <small v-if="step.key === bottleneckKey">瓶颈</small>
              </div>
              <div v-if="index < flowSteps.length - 1" class="flow-arrow" :class="{ flowing: flowPlaybackIndex > index || measurementProgress === 'done' }"></div>
            </template>
          </div>

          <div class="flow-summary">
            <div>
              <strong>总耗时</strong>
              <span class="highlight">{{ formatMetric(networkReport.totalMs) }}</span>
            </div>
            <div>
              <strong>主要瓶颈</strong>
              <span>{{ flowSteps.find(step => step.key === bottleneckKey)?.title || 'n/a' }}</span>
            </div>
          </div>
        </section>

        <!-- 安全评分卡 -->
        <section v-if="networkReport" class="security-score-card">
          <div class="score-main">
            <div class="score-circle" :style="{ '--score-color': getScoreColor(currentScore), '--score-deg': `${currentScore * 3.6}deg` } as any">
              <div class="score-value">{{ currentScore }}</div>
              <div class="score-grade">{{ getScoreGrade(currentScore) }}</div>
            </div>
            <div class="score-details">
              <p class="eyebrow">安全评分</p>
              <h3>{{ networkReport.host }} 网络安全整体评估</h3>
              <p>{{ currentScore >= 80 ? '安全水平高' : currentScore >= 60 ? '存在改进空间' : '需要立即改善' }}</p>
              <div class="health-pill-row">
                <span :class="networkReport.riskLevel === 'high' ? 'danger' : networkReport.riskLevel === 'medium' ? 'warn' : 'safe'">
                  风险等级：{{ networkReport.riskLevel }}
                </span>
                <span>证书剩余：{{ networkReport.certificateDaysRemaining ?? 'n/a' }} 天</span>
                <span>安全头覆盖：{{ headerCoverage }}%</span>
              </div>
            </div>
          </div>

          <div class="score-breakdown">
            <div class="score-item">
              <div class="item-label">
                <span>HTTPS 启用</span>
                <span :class="networkReport.httpsEnabled ? 'status-pass' : 'status-fail'">
                  {{ networkReport.httpsEnabled ? '✅' : '❌' }}
                </span>
              </div>
              <div class="item-bar" :class="networkReport.httpsEnabled ? 'bar-pass' : 'bar-fail'"></div>
            </div>

            <div class="score-item">
              <div class="item-label">
                <span>证书有效</span>
                <span :class="networkReport.certificateValid ? 'status-pass' : 'status-fail'">
                  {{ networkReport.certificateValid ? '✅' : '❌' }}
                </span>
              </div>
              <div class="item-bar" :class="networkReport.certificateValid ? 'bar-pass' : 'bar-fail'"></div>
            </div>

            <div class="score-item">
              <div class="item-label">
                <span>安全响应头</span>
                <span class="status-info">{{ networkReport.securityHeaders.filter(h => h.present).length }}/{{ networkReport.securityHeaders.length }}</span>
              </div>
              <div class="item-bar bar-info" :style="{ width: `${(networkReport.securityHeaders.filter(h => h.present).length / networkReport.securityHeaders.length) * 100}%` }"></div>
            </div>

            <div class="score-item">
              <div class="item-label">
                <span>HTTP 状态</span>
                <span :class="(networkReport.httpStatus && networkReport.httpStatus < 400) ? 'status-pass' : 'status-fail'">
                  {{ networkReport.httpStatus || 'n/a' }}
                </span>
              </div>
              <div class="item-bar" :class="(networkReport.httpStatus && networkReport.httpStatus < 400) ? 'bar-pass' : 'bar-fail'"></div>
            </div>

            <div class="score-item">
              <div class="item-label">
                <span>响应速度</span>
                <span :class="(networkReport.totalMs && networkReport.totalMs < 2000) ? 'status-pass' : 'status-warn'">
                  {{ formatMetric(networkReport.totalMs) }}
                </span>
              </div>
              <div class="item-bar" :class="(networkReport.totalMs && networkReport.totalMs < 2000) ? 'bar-pass' : (networkReport.totalMs && networkReport.totalMs < 5000) ? 'bar-warn' : 'bar-fail'"></div>
            </div>
          </div>
        </section>

        <section class="network-agent-card listener-card">
          <div>
            <p class="eyebrow">Web Request Listener</p>
            <h3>监听地址</h3>
            <p>{{ listenerStatus?.captureUrl || 'http://localhost:8090/api/network/listener/capture' }}</p>
          </div>
          <div class="agent-action-row">
            <button class="primary-button" type="button" @click="copyListenerUrl">复制地址</button>
            <button class="ghost-button" type="button" :disabled="listenerLoading" @click="refreshListener">
              {{ listenerLoading ? '刷新中' : '刷新日志' }}
            </button>
            <button class="ghost-button" type="button" @click="exportListener('docx')">导出 Word</button>
            <button class="ghost-button" type="button" @click="exportListener('xlsx')">导出 Excel</button>
            <button class="danger-button" type="button" @click="clearListener">清空</button>
          </div>
          <div v-if="listenerEvents.length" class="listener-list">
            <article v-for="event in listenerEvents.slice(0, 8)" :key="event.id" class="listener-event">
              <div>
                <strong>{{ event.method }} {{ event.path }}</strong>
                <span>{{ event.sourceIp }} / {{ formatDeadline(event.capturedAt) }}</span>
              </div>
              <p>{{ event.userAgent || 'No User-Agent' }}</p>
              <small>{{ event.riskHints.join('；') }}</small>
            </article>
          </div>
          <p v-else class="empty-state">暂无监听日志。可以向上面的地址发送 GET 或 POST 请求进行演示。</p>
        </section>

        <section v-if="networkReport" class="network-report-grid">
          <article class="network-summary-card">
            <p class="eyebrow">Risk</p>
            <h3 :class="`risk-${networkReport.riskLevel}`">{{ networkReport.riskLevel }}</h3>
            <p>{{ networkReport.summary }}</p>
            <div class="agent-action-row report-export-row">
              <button class="ghost-button" type="button" @click="exportReport('docx')">导出 Word</button>
              <button class="ghost-button" type="button" @click="exportReport('xlsx')">导出 Excel</button>
            </div>
          </article>

          <article class="network-summary-card">
            <p class="eyebrow">Web Security</p>
            <div class="security-lines">
              <span>HTTPS：{{ networkReport.httpsEnabled ? '已启用' : '未启用' }}</span>
              <span>TLS 证书：{{ networkReport.certificateValid ? '有效' : '异常' }}</span>
              <span v-if="networkReport.certificateDaysRemaining !== undefined">
                证书剩余：{{ networkReport.certificateDaysRemaining }} 天
              </span>
              <span>状态码：{{ networkReport.httpStatus || 'n/a' }} {{ networkReport.httpStatusText || '' }}</span>
            </div>
          </article>

          <article class="network-summary-card network-metrics-card">
            <p class="eyebrow">Network Measurement</p>
            <div class="metric-grid">
              <div><strong>{{ formatMetric(networkReport.dnsMs) }}</strong><span>DNS</span></div>
              <div><strong>{{ formatMetric(networkReport.tcpMs) }}</strong><span>TCP</span></div>
              <div><strong>{{ formatMetric(networkReport.tlsMs) }}</strong><span>TLS</span></div>
              <div><strong>{{ formatMetric(networkReport.ttfbMs) }}</strong><span>TTFB</span></div>
              <div><strong>{{ formatMetric(networkReport.totalMs) }}</strong><span>Total</span></div>
              <div><strong>{{ networkReport.responseBytes || 0 }}</strong><span>Bytes</span></div>
            </div>
          </article>

          <article class="network-summary-card">
            <p class="eyebrow">Resolved Target</p>
            <h3>{{ networkReport.host }}</h3>
            <p>{{ networkReport.finalUrl || networkReport.target }}</p>
            <small>{{ networkReport.ipAddresses.join(' / ') }}</small>
          </article>
        </section>

        <section v-if="networkReport" class="network-detail-grid">
          <article>
            <h3>安全响应头</h3>
            <div class="header-check-list">
              <div
                v-for="header in networkReport.securityHeaders"
                :key="header.name"
                class="header-check"
                :class="{ missing: !header.present }"
              >
                <div>
                  <strong>{{ header.name }}</strong>
                  <span>{{ header.present ? '已配置' : '缺失' }}</span>
                </div>
                <p>{{ header.present ? header.value : header.recommendation }}</p>
              </div>
            </div>
          </article>

          <article>
            <h3>风险与修复建议</h3>
            <div v-if="networkReport.risks.length" class="risk-list">
              <p v-for="risk in networkReport.risks" :key="risk">{{ risk }}</p>
            </div>
            <p v-else class="empty-state">当前未发现明显 HTTPS、TLS 或安全响应头风险。</p>
          </article>
        </section>

        <section class="network-history">
          <h3>历史检测报告</h3>
          <div class="execution-list">
            <button
              v-for="report in networkReports"
              :key="report.id"
              class="network-history-item"
              @click="networkReport = report"
            >
              <strong>{{ report.host }}</strong>
              <span>{{ report.riskLevel }} / {{ report.totalMs || 'n/a' }} ms / {{ report.httpStatus || 'n/a' }}</span>
            </button>
          </div>
        </section>
      </section>

      <section v-else-if="activePage === 'settings'" class="page settings-page">
        <header class="page-head">
          <div>
            <p class="eyebrow">Settings</p>
            <h2>模型设置</h2>
          </div>
          <div class="head-actions">
            <button class="ghost-button" :disabled="settingsTesting" @click="testRuntimeSettings">
              {{ settingsTesting ? '测试中' : '测试连接' }}
            </button>
            <button class="primary-button" @click="saveRuntimeSettings(true)">保存 API</button>
          </div>
        </header>

        <div class="settings-grid">
          <div class="preset-actions">
            <button class="ghost-button" type="button" @click="useDeepSeekOfficialPreset">DeepSeek 官方</button>
            <button class="ghost-button" type="button" @click="useSchoolGatewayPreset">学校网关</button>
            <button class="ghost-button" type="button" @click="clearModelUrlForAuto">只填 Key 自动识别</button>
          </div>

          <label>
            <span>模型供应商</span>
            <select v-model="settings.provider">
              <option v-for="provider in providerOptions" :key="provider.provider" :value="provider.provider">
                {{ provider.displayName }}
              </option>
            </select>
          </label>

          <label>
            <span>Model URL</span>
            <input v-model="settings.baseUrl" placeholder="可留空；留空时按 API Key 自动使用 DeepSeek 官方配置" />
          </label>

          <label>
            <span>接口路径</span>
            <select v-model="settings.apiPath">
              <option value="/api/v1/chat/completions">学校网关 /api/v1/chat/completions</option>
              <option value="/chat/completions">DeepSeek 官方 /chat/completions</option>
              <option value="/v1/chat/completions">OpenAI /v1/chat/completions</option>
              <option value="auto">自动补全</option>
            </select>
          </label>

          <label>
            <span>API Key</span>
            <input
              v-model="settings.apiKey"
              type="password"
              :placeholder="backendKeyPreview ? `后端已保存 ${backendKeyPreview}，留空则继续使用` : 'API Key'"
              autocomplete="off"
            />
          </label>

          <label>
            <span>模型名</span>
            <input v-model="settings.model" placeholder="default" />
          </label>

          <label>
            <span>鉴权方式</span>
            <select v-model="settings.authType">
              <option value="auto">自动</option>
              <option value="bearer">Authorization: Bearer</option>
              <option value="x-api-key">X-API-Key</option>
            </select>
          </label>
        </div>

        <section class="settings-flow">
          <article>
            <p class="eyebrow">OpenClaw-style Flow</p>
            <h3>飞书消息驱动网页 Agent</h3>
            <p>用户在飞书里 @机器人发送任务，后端回调会进入 Agent 编排器，读取这里保存的大模型 API，自动选择 skill，执行后把结果回到飞书，同时同步到网页日志。</p>
          </article>
          <article>
            <p class="eyebrow">Runtime API</p>
            <h3>{{ settingsStatus || '等待保存或测试' }}</h3>
            <p>当前模型：{{ settings.model || 'deepseek' }} / {{ settings.authType || 'auto' }}</p>
            <p>密钥状态：{{ backendKeyPreview ? `后端已保存 ${backendKeyPreview}` : '尚未保存' }}</p>
          </article>
        </section>
      </section>

      <section v-else class="page">
        <header class="page-head">
          <div>
            <p class="eyebrow">Feishu Bot</p>
            <h2>飞书控制</h2>
          </div>
          <div class="model-pill feishu-pill">
            <span>reply mode</span>
            <strong>{{ feishuReplyMode }}</strong>
          </div>
        </header>

        <section class="feishu-dashboard">
          <article class="feishu-card status-card" :class="{ live: feishuReady }">
            <p class="eyebrow">Webhook Ready</p>
            <h3>{{ feishuReady ? '自动回复已准备好' : '当前仅预览模式' }}</h3>
            <p>Request URL: {{ feishuHealth?.publicCallbackUrl || feishuHealth?.callbackPath || '/api/feishu/events' }}</p>
            <div class="status-row">
              <span class="status-chip" :class="{ live: feishuReady }">
                {{ feishuReady ? '凭证已配置' : '缺少飞书凭证' }}
              </span>
              <span class="status-chip muted">
                Token {{ feishuHealth?.verificationTokenConfigured ? '已配置' : '未配置' }}
              </span>
            </div>
          </article>

          <article class="feishu-card">
            <p class="eyebrow">Quick Checks</p>
            <ul class="flow-list">
              <li>1. 在飞书开放平台把事件回调指向上面的 Request URL。</li>
              <li>2. 配置 App ID / Secret，确保后端能发出真实消息。</li>
              <li>3. 在群里 @机器人 发“检测 https://example.com”或“导出网络报告”验证回复。</li>
            </ul>
          </article>
        </section>

        <div class="notice-box">
          <p>Request URL: {{ feishuHealth?.publicCallbackUrl || '/api/feishu/events' }}</p>
          <p>支持命令：检测网站、开启监听、导出网络报告、生成 Word/Excel 分析、帮助</p>
          <p>API 配置命令：配置API sk-xxxx；如需学校网关：配置API 学校 sk-xxxx</p>
        </div>

        <section class="feishu-roadmap">
          <article>
            <p class="eyebrow">Next Features</p>
            <h3>下一步建议做什么</h3>
            <div class="roadmap-list">
              <div v-for="tip in feishuTips" :key="tip" class="roadmap-item">
                <span></span>
                <p>{{ tip }}</p>
              </div>
            </div>
          </article>
          <article>
            <p class="eyebrow">Status</p>
            <h3>配置状态</h3>
            <div class="status-grid">
              <div class="status-metric">
                <strong>{{ feishuHealth?.botCredentialsReady ? 'OK' : 'NO' }}</strong>
                <span>Bot Credentials</span>
              </div>
              <div class="status-metric">
                <strong>{{ feishuHealth?.defaultChatConfigured ? 'OK' : 'NO' }}</strong>
                <span>Default Chat</span>
              </div>
              <div class="status-metric">
                <strong>{{ feishuHealth?.verificationTokenConfigured ? 'OK' : 'NO' }}</strong>
                <span>Verification Token</span>
              </div>
            </div>
          </article>
        </section>

        <h3>最近飞书指令</h3>
        <div v-if="feishuEvents.length" class="message-list event-list">
          <article v-for="(message, index) in feishuEvents" :key="index" class="message" :class="message.role">
            <span class="avatar">{{ message.role === 'user' ? '飞书' : 'AI' }}</span>
            <p>{{ message.content }}</p>
          </article>
        </div>
        <p v-else class="empty-state">暂无飞书事件</p>
      </section>

      <section v-if="notice" class="notice-box floating-notice">
        <p>{{ notice }}</p>
      </section>
    </section>
  </main>
</template>

