import { chromium } from 'playwright'
import { createInterface } from 'node:readline/promises'
import { stdin as input, stdout as output } from 'node:process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import fs from 'node:fs/promises'
import crypto from 'node:crypto'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '../..')
const dataDir = path.join(repoRoot, 'backend', 'data')
const userDataDir = path.join(dataDir, 'rain-classroom-browser')
const sessionPath = path.join(dataDir, 'rain-classroom-session.json')
const cachePath = path.join(dataDir, 'rain-classroom-assignments.json')
const discoveryPath = path.join(dataDir, 'rain-classroom-discovery.json')
const startUrl = process.env.RAIN_CLASSROOM_URL || 'https://www.yuketang.cn/'
const browserChannel = process.env.RAIN_BROWSER_CHANNEL || ''
const allowedHosts = ['yuketang.cn', 'www.yuketang.cn', 'pro.yuketang.cn', 'www.xuetangx.com']

const rl = createInterface({ input, output })
const seenAssignments = new Map()
const discoveredEndpoints = new Map()

function isRainClassroomUrl(url) {
  try {
    const host = new URL(url).hostname
    return allowedHosts.some((allowed) => host === allowed || host.endsWith(`.${allowed}`))
  } catch {
    return false
  }
}

function text(value) {
  if (value == null) return ''
  return String(value).trim()
}

function firstText(...values) {
  return values.map(text).find(Boolean) || ''
}

function parseDeadline(value) {
  if (value == null || value === '') return ''
  if (typeof value === 'number') {
    const ms = value > 10_000_000_000 ? value : value * 1000
    return new Date(ms).toISOString()
  }
  const raw = String(value).trim()
  if (!raw) return ''
  const normalized = raw.replace(/年|-/g, '-').replace(/月/g, '-').replace(/日/g, ' ').replace(/\//g, '-')
  const parsed = new Date(normalized)
  return Number.isNaN(parsed.getTime()) ? '' : parsed.toISOString()
}

function hasAssignmentShape(item) {
  if (!item || typeof item !== 'object' || Array.isArray(item)) return false
  const title = firstText(
    item.title,
    item.name,
    item.homework_title,
    item.exercise_name,
    item.assignment_name,
    item.content_name,
    item.task_name,
  )
  const deadline = firstText(
    item.deadline,
    item.due_time,
    item.end_time,
    item.endTime,
    item.expire_time,
    item.submit_end_time,
    item.close_time,
  )
  return Boolean(title && deadline)
}

function normalizeAssignment(item, url) {
  const title = firstText(
    item.title,
    item.name,
    item.homework_title,
    item.exercise_name,
    item.assignment_name,
    item.content_name,
    item.task_name,
  )
  const course = firstText(
    item.course_name,
    item.courseName,
    item.class_name,
    item.className,
    item.pro_name,
    item.lesson_name,
    item.org_name,
    '雨课堂课程',
  )
  const deadline = parseDeadline(
    item.deadline ??
      item.due_time ??
      item.end_time ??
      item.endTime ??
      item.expire_time ??
      item.submit_end_time ??
      item.close_time,
  )
  if (!title || !deadline) return null
  const stable = firstText(item.id, item.homework_id, item.exercise_id, item.content_id, item.task_id, `${course}-${title}-${deadline}`)
  return {
    id: `rain-${crypto.createHash('sha1').update(stable).digest('hex').slice(0, 12)}`,
    platform: '雨课堂',
    course,
    title,
    status: firstText(item.status, item.submit_status, item.state, item.finished ? '已完成' : '', '待确认'),
    deadline,
    sourceUrl: url,
  }
}

function walk(value, url, depth = 0, outputItems = []) {
  if (depth > 8 || value == null) return outputItems
  if (Array.isArray(value)) {
    for (const item of value) walk(item, url, depth + 1, outputItems)
    return outputItems
  }
  if (typeof value !== 'object') return outputItems
  if (hasAssignmentShape(value)) {
    const normalized = normalizeAssignment(value, url)
    if (normalized) outputItems.push(normalized)
  }
  for (const child of Object.values(value)) walk(child, url, depth + 1, outputItems)
  return outputItems
}

async function saveState(context, message) {
  const assignments = [...seenAssignments.values()].sort((a, b) => new Date(a.deadline) - new Date(b.deadline))
  const payload = {
    source: assignments.length ? 'rain-classroom' : 'demo',
    status: assignments.length ? 'real-cache' : 'waiting-for-capture',
    message,
    syncedAt: new Date().toISOString(),
    assignments,
    discoveredEndpoints: [...discoveredEndpoints.values()],
  }
  await fs.mkdir(dataDir, { recursive: true })
  await fs.writeFile(cachePath, JSON.stringify(payload, null, 2), 'utf8')
  await fs.writeFile(discoveryPath, JSON.stringify(payload.discoveredEndpoints, null, 2), 'utf8')
  await context.storageState({ path: sessionPath })
  return payload
}

async function main() {
  await fs.mkdir(dataDir, { recursive: true })
  const context = await chromium.launchPersistentContext(userDataDir, {
    ...(browserChannel ? { channel: browserChannel } : {}),
    headless: false,
    viewport: { width: 1360, height: 860 },
  })
  const page = context.pages()[0] || (await context.newPage())

  context.on('response', async (response) => {
    const url = response.url()
    if (!isRainClassroomUrl(url)) return
    const contentType = response.headers()['content-type'] || ''
    if (!contentType.includes('json')) return
    try {
      const body = await response.json()
      const assignments = walk(body, url)
      if (!assignments.length) return
      discoveredEndpoints.set(url, {
        url,
        status: response.status(),
        capturedAt: new Date().toISOString(),
        count: assignments.length,
      })
      for (const assignment of assignments) {
        seenAssignments.set(assignment.id, assignment)
      }
      const saved = await saveState(context, `已捕获 ${seenAssignments.size} 条雨课堂作业。`)
      console.log(`[rain] captured ${saved.assignments.length} assignments from ${url}`)
    } catch {
      // Some JSON-like endpoints are not parseable through Playwright after redirects or streaming.
    }
  })

  console.log('\n[rain] 将打开一个独立浏览器窗口。请手动登录雨课堂，然后进入课程/作业/待办页面。')
  console.log(`[rain] 浏览器：${browserChannel || 'Playwright Chromium'}。如需系统 Chrome/Edge，请用 npm run rain:login:chrome 或 npm run rain:login:edge。`)
  console.log(`[rain] 会话保存到：${sessionPath}`)
  console.log(`[rain] 作业缓存保存到：${cachePath}\n`)
  await page.goto(startUrl, { waitUntil: 'domcontentloaded' })
  await rl.question('登录并打开作业页面后，确认页面已加载，再按 Enter 结束抓包并保存缓存...')
  const saved = await saveState(context, seenAssignments.size ? `已捕获 ${seenAssignments.size} 条雨课堂作业。` : '未捕获到作业接口，请进入雨课堂作业/待办页面后重试。')
  console.log(`\n[rain] 完成。捕获作业数：${saved.assignments.length}`)
  console.log(`[rain] 后端会读取：${cachePath}`)
  await context.close()
  rl.close()
}

main().catch(async (error) => {
  console.error('[rain] 抓包失败：', error)
  rl.close()
  process.exit(1)
})
