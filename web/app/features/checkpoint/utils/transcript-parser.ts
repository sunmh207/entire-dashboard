/**
 * Transcript parser for full.jsonl (OpenCode/Claude Code format)
 * Parses messages, file changes, tools used. Steps = user→assistant turn count.
 */

export interface FileDiff {
  file: string
  before: string
  after: string
  additions: number
  deletions: number
}

export interface ToolUse {
  callID: string
  tool: string
  input: unknown
  output?: unknown
  title?: string
}

export interface MessageView {
  id: string
  role: 'user' | 'assistant'
  text: string
  taskTitle?: string
  diffs?: FileDiff[]
  tools: ToolUse[]
  toolsCount: number
  reasoning?: string
  createdAt?: number
}

/// Metadata from `GET /session/transcript/normalized` (backend-normalized transcript).
export interface NormalizedTranscriptMeta {
  sourceFormat: string
  rawBytesLength: number
  warnings?: string[]
}

export interface ParsedTranscript {
  schemaVersion?: number
  meta?: NormalizedTranscriptMeta
  title?: string
  projectRoot?: string
  messages: MessageView[]
  fileChanges: { file: string; additions: number; deletions: number }[]
  stepsCount: number
}

interface TranscriptMessage {
  info?: {
    id?: string
    role?: string
    time?: { created?: number }
    summary?: { title?: string; diffs?: FileDiff[] }
  }
  parts?: Array<{
    type: string
    text?: string
    tool?: string
    callID?: string
    state?: { input?: unknown; output?: unknown; title?: string }
  }>
}

interface TranscriptRoot {
  info?: { title?: string; directory?: string }
  messages?: TranscriptMessage[]
}

/**
 * Strip project root from file path to get relative path
 */
export function stripProjectRoot(filePath: string, projectRoot?: string): string {
  if (!projectRoot) return filePath
  const normalized = filePath.replace(/\\/g, '/')
  const root = projectRoot.replace(/\\/g, '/').replace(/\/$/, '')
  if (normalized.startsWith(root + '/')) {
    return normalized.slice(root.length + 1)
  }
  return filePath
}

/**
 * Parse full.jsonl transcript
 */
export function parseTranscript(raw: string): ParsedTranscript | null {
  try {
    const root: TranscriptRoot = JSON.parse(raw)
    const messages: TranscriptMessage[] = root.messages ?? []
    const projectRoot = root.info?.directory

    const messageViews: MessageView[] = []
    const fileChangeMap = new Map<string, { additions: number; deletions: number }>()
    let stepsCount = 0

    for (const msg of messages) {
      const info = msg.info ?? {}
      const role = info.role === 'user' ? 'user' : 'assistant'
      const parts = msg.parts ?? []

      const textParts = parts.filter((p) => p.type === 'text').map((p) => p.text ?? '').join('\n')
      const reasoningPart = parts.find((p) => p.type === 'reasoning')
      const toolParts = parts.filter((p) => p.type === 'tool')
      const tools: ToolUse[] = toolParts.map((p) => ({
        callID: p.callID ?? '',
        tool: p.tool ?? '',
        input: p.state?.input,
        output: p.state?.output,
        title: p.state?.title,
      }))

      if (role === 'user') {
        stepsCount++
      }

      const diffs = info.summary?.diffs
      if (diffs) {
        for (const d of diffs) {
          const relPath = stripProjectRoot(d.file, projectRoot)
          const existing = fileChangeMap.get(relPath)
          const add = d.additions ?? 0
          const del = d.deletions ?? 0
          if (existing) {
            existing.additions += add
            existing.deletions += del
          } else {
            fileChangeMap.set(relPath, { additions: add, deletions: del })
          }
        }
      }

      messageViews.push({
        id: info.id ?? '',
        role,
        text: textParts.trim(),
        taskTitle: info.summary?.title,
        diffs: diffs?.map((d) => ({
          ...d,
          file: stripProjectRoot(d.file, projectRoot),
        })),
        tools,
        toolsCount: tools.length,
        reasoning: reasoningPart?.text,
        createdAt: info.time?.created,
      })
    }

    const fileChanges = Array.from(fileChangeMap.entries()).map(([file, stats]) => ({
      file,
      additions: stats.additions,
      deletions: stats.deletions,
    }))

    return {
      title: root.info?.title,
      projectRoot,
      messages: messageViews,
      fileChanges,
      stepsCount,
    }
  } catch {
    return null
  }
}
