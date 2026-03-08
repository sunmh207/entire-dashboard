/**
 * Repository module - Backend API contract (DTO)
 */

export interface RepoDTO {
  id: number
  name: string
  webUrl: string
  platform: string
  accessToken: string
  createdAt: number
  updatedAt: number
  /** Last successful checkpoint sync timestamp (ms), null if never synced */
  lastSuccessfulSyncAt?: number | null
}

export interface RepoRowVO extends RepoDTO {
  /** Whether to show delete confirmation popover */
  showDeleteConfirm: boolean
}

export interface RepoCreateParams {
  name: string
  webUrl: string
  platform: string
  accessToken?: string
}

export interface RepoUpdateParams {
  id: number
  name: string
  webUrl: string
  platform: string
  accessToken?: string
}

export interface RepoSearchParams {
  keyword?: string
}
