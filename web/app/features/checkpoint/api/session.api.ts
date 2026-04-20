/**
 * Session module - API client
 */
import { $adminApi } from '~/api/admin-api-client'
import type { ParsedTranscript } from '../utils/transcript-parser'
import type { SessionDTO } from '../types/session.types'

export const sessionApi = {
  /** List sessions by checkpoint (Checkpoint.id) */
  list(checkpointId: number): Promise<SessionDTO[]> {
    return $adminApi<SessionDTO[]>(`/session/list?checkpointId=${checkpointId}`, { method: 'GET' })
  },

  /** Get session by ID */
  get(sessionId: number): Promise<SessionDTO> {
    return $adminApi<SessionDTO>(`/session/get?id=${sessionId}`, { method: 'GET' })
  },

  /** Get session content: prompt, context, or transcript (full.jsonl) */
  getContent(sessionId: number, file: 'prompt' | 'context' | 'transcript'): Promise<string> {
    return $adminApi<string>(`/session/content?sessionId=${sessionId}&file=${file}`, {
      method: 'GET',
    })
  },

  /** Backend-normalized transcript (OpenCode JSON, Cursor NDJSON, etc.) */
  getNormalizedTranscript(sessionId: number): Promise<ParsedTranscript> {
    return $adminApi<ParsedTranscript>(`/session/transcript/normalized?sessionId=${sessionId}`, {
      method: 'GET',
    })
  },
}
