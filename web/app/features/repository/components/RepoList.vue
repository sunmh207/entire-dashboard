<script setup lang="tsx">
import { UButton, UDropdownMenu } from '#components'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import { repoApi } from '~/features/repository/api/repo.api'
import { checkpointApi } from '~/features/checkpoint/api/checkpoint.api'
import type { RepoRowVO } from '~/features/repository/types/repoDTO'
import RepoCreateSlideover from '~/features/repository/components/RepoCreateSlideover.vue'
import RepoEditSlideover from '~/features/repository/components/RepoEditSlideover.vue'
import Popconfirm from '~/shared/components/Popconfirm.vue'
import { useMessage } from '~/shared/composables/useMessage'
import { useSearchPagination } from '~/shared/composables/useSearchPagination'

const ONE_DAY_MS = 24 * 60 * 60 * 1000

const message = useMessage()

const { conditions, pager, pageSizeOptions } = useSearchPagination(
  { keyword: '' },
  { defaultSize: 20 }
)

const { data: rows, refresh } = await repoApi.search(conditions, pager)

const tableData = ref<RepoRowVO[]>([])
watch(
  () => rows.value?.data,
  (data) => {
    tableData.value = (data || []).map((item) => ({ ...item, showDeleteConfirm: false }))
  },
  { immediate: true }
)

const editModalRef = ref<InstanceType<typeof RepoEditSlideover> | null>(null)

const platformMap: Record<string, string> = {
  GITHUB: 'GitHub',
  GITLAB: 'GitLab',
  GITEE: 'Gitee',
  GITEA: 'Gitea',
}

const columns = [
  {
    accessorKey: 'name',
    header: 'Name',
    cell: ({ row }: { row: Row<RepoRowVO> }) => {
      return (
        <div>
          <div class="text-default">{row.original.name}</div>
        </div>
      )
    },
  },
  {
    accessorKey: 'webUrl',
    header: 'URL',
    cell: ({ row }: { row: Row<RepoRowVO> }) => {
      return (
        <a
          href={row.original.webUrl}
          target="_blank"
          class="text-primary hover:underline truncate max-w-[380px] block"
          title={row.original.webUrl}
        >
          {row.original.webUrl}
        </a>
      )
    },
  },
  {
    accessorKey: 'platform',
    header: 'Platform',
    cell: ({ row }: { row: Row<RepoRowVO> }) => {
      return <div class="text-default">{platformMap[row.original.platform] || row.original.platform}</div>
    },
  },
  {
    accessorKey: 'createdAt',
    header: 'Created',
    cell: ({ row }: { row: Row<RepoRowVO> }) => {
      const ts = row.original.createdAt
      if (!ts) return <div class="text-default">-</div>
      const date = new Date(ts)
      return <div class="text-default">{date.toLocaleDateString()}</div>
    },
  },
  {
    accessorKey: 'lastSuccessfulSyncAt',
    header: 'Last Synced',
    cell: ({ row }: { row: Row<RepoRowVO> }) => {
      const ts = row.original.lastSuccessfulSyncAt
      const isStale = !ts || Date.now() - ts > ONE_DAY_MS
      const text = ts ? new Date(ts).toLocaleString() : 'Never synced'
      return (
        <div class={isStale ? 'text-amber-600 dark:text-amber-500' : 'text-default'}>
          {text}
        </div>
      )
    },
  },
  {
    id: 'actions',
    cell: ({ row }: { row: Row<RepoRowVO> }) => {
      return (
        <div class="text-right">
          <UDropdownMenu content={{ align: 'end' }} items={getRowMenuItems(row)}>
            <UButton icon="i-lucide-ellipsis-vertical" color="neutral" variant="ghost" class="ml-auto" />
          </UDropdownMenu>
          <Popconfirm
            open={row.original.showDeleteConfirm}
            onUpdate:open={(val) => (row.original.showDeleteConfirm = val)}
            title="Confirm delete"
            description={`Are you sure you want to delete repository ${row.original.name}?`}
            color="error"
            content={{ align: 'start', side: 'left' }}
            onOk={() => handleDelete(row.original)}
          >
            <span></span>
          </Popconfirm>
        </div>
      )
    },
  },
]

const syncModalOpen = ref(false)
const syncLoading = ref(false)
const syncRepoName = ref('')
const syncError = ref<string | null>(null)

const getRowMenuItems = (row: Row<RepoRowVO>): DropdownMenuItem[] => {
  return [
    {
      label: 'Edit',
      icon: 'i-lucide-square-pen',
      onSelect: () => {
        editModalRef.value?.openEdit(row.original)
      },
    },
    {
      label: 'Sync',
      icon: 'i-lucide-refresh-cw',
      onSelect: () => {
        openSyncModal(row.original)
      },
    },
    {
      label: 'Delete',
      icon: 'i-lucide-trash-2',
      onSelect: () => {
        row.original.showDeleteConfirm = true
      },
    },
  ]
}

function openSyncModal(repo: RepoRowVO) {
  syncRepoName.value = repo.name
  syncError.value = null
  syncModalOpen.value = true
  syncLoading.value = true
  checkpointApi
    .syncRepo(repo.id)
    .then(() => {
      syncLoading.value = false
      message.success(`Repository ${repo.name} synced successfully`)
      refresh()
      setTimeout(() => {
        syncModalOpen.value = false
      }, 500)
    })
    .catch((err: any) => {
      syncLoading.value = false
      syncError.value = err?.data?.message || err?.message || 'Sync failed'
    })
}

function closeSyncModal() {
  syncModalOpen.value = false
  syncRepoName.value = ''
  syncError.value = null
}

const resetFilters = async () => {
  conditions.keyword = ''
  pager.page = 1
  await refresh()
}

const handleDelete = async (repo: RepoRowVO) => {
  try {
    const result = await repoApi.delete(repo.id)

    if (result.code === 'Ok') {
      await refresh()
      repo.showDeleteConfirm = false
      message.success(`Repository ${repo.name} has been deleted`)
    } else {
      repo.showDeleteConfirm = false
      message.error(result.message || 'Delete failed')
    }
  } catch {
    repo.showDeleteConfirm = false
    message.error('Delete failed')
  }
}

const handleEditOk = () => {
  refresh()
}
</script>

<template>
  <div class="space-y-4 mt-2">
    <div class="flex gap-2 items-center">
      <UInput v-model="conditions.keyword" placeholder="Name / URL" class="w-64" :ui="{ trailing: 'pr-0.5' }">
        <template v-if="conditions.keyword.length" #trailing>
          <UButton
            color="neutral"
            variant="link"
            size="sm"
            icon="i-lucide-circle-x"
            aria-label="Clear input"
            @click="conditions.keyword = ''"
          />
        </template>
      </UInput>
      <UButton label="Reset" color="neutral" variant="outline" size="md" @click="resetFilters" />

      <div class="ml-auto">
        <RepoCreateSlideover @ok="() => refresh()" />
      </div>
    </div>
    <UTable :data="tableData" :columns="columns" class="flex-1" />

    <div class="flex justify-between items-center mt-4">
      <div class="text-sm text-default">Total {{ rows?.total || 0 }} items</div>
      <div class="flex items-center gap-3">
        <UPagination v-model:page="pager.page" :items-per-page="pager.size" :total="rows?.total || 0" :max="7" />
        <USelect v-model="pager.size" :items="pageSizeOptions" class="w-36" />
      </div>
    </div>
  </div>

  <RepoEditSlideover ref="editModalRef" @ok="handleEditOk" />

  <UModal v-model:open="syncModalOpen" :dismissible="!syncLoading">
    <template #content>
      <div class="p-6 space-y-4">
        <div class="flex items-center gap-3">
          <UIcon
            v-if="syncLoading"
            name="i-lucide-loader-2"
            class="w-6 h-6 text-primary animate-spin"
          />
          <UIcon v-else-if="syncError" name="i-lucide-circle-alert" class="w-6 h-6 text-error" />
          <UIcon v-else name="i-lucide-circle-check" class="w-6 h-6 text-success" />
          <div>
            <div class="font-medium">
              {{ syncLoading ? 'Syncing...' : syncError ? 'Sync Failed' : 'Sync Complete' }}
            </div>
            <div class="text-sm text-muted">
              {{ syncRepoName }}
            </div>
          </div>
        </div>
        <p v-if="syncError" class="text-sm text-error">
          {{ syncError }}
        </p>
        <div v-if="!syncLoading" class="flex justify-end">
          <UButton color="neutral" variant="outline" @click="closeSyncModal">
            Close
          </UButton>
        </div>
      </div>
    </template>
  </UModal>
</template>
