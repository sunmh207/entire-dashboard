/**
 * Repository module - API client
 */
import type { UnwrapRef } from 'vue'
import { useAdminApi, $adminApi } from '~/api/admin-api-client'
import type { RepoSearchParams, RepoDTO, RepoCreateParams, RepoUpdateParams } from '../types/repoDTO'
import type { PagerPayload, StatusResult } from '~/shared/types/api'
import type { Pager } from '~/shared/composables/useSearchPagination'

export const repoApi = {
  /** Search repositories (paginated) */
  async search(conditions: UnwrapRef<RepoSearchParams>, pager: UnwrapRef<Pager>) {
    return useAdminApi<PagerPayload<RepoDTO>>('/repo/search', {
      query: computed(() => ({
        ...conditions,
        page: pager.page - 1, // Backend uses 0-based page
        size: pager.size,
      })),
    })
  },

  /** Create repository */
  async create(params: RepoCreateParams) {
    return $adminApi<RepoDTO>('/repo/create', {
      method: 'POST',
      body: params,
    })
  },

  /** Update repository */
  async update(params: RepoUpdateParams) {
    return $adminApi<RepoDTO>('/repo/update', {
      method: 'POST',
      body: params,
    })
  },

  /** Delete repository */
  async delete(id: number) {
    return $adminApi<StatusResult<void>>('/repo/delete', {
      method: 'POST',
      body: { id },
    })
  },

  /** Get single repository */
  async get(id: number) {
    return $adminApi<RepoDTO>('/repo/get', {
      method: 'GET',
      query: { id },
    })
  },

  /** Validate access token against platform API */
  async validateToken(params: { webUrl: string; platform: string; accessToken: string }) {
    return $adminApi<TokenValidateResult>('/repo/validate-token', {
      method: 'POST',
      body: params,
    })
  },
}

export interface TokenValidateResult {
  valid: boolean
  message?: string
}
