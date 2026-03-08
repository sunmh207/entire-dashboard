<script setup lang="ts">
import { repoApi } from '../api/repo.api'
import type { FormSubmitEvent } from '#ui/types'
import { PLATFORM_OPTIONS } from '../constants/repo.constants'
import { repoFormSchema } from '../schemas/repo.schema'
import type { RepoCreateParams, RepoDTO } from '~/features/repository/types/repoDTO'

const emit = defineEmits<{
  ok: [repo: RepoDTO]
}>()

const message = useMessage()
const toast = useToast()
const formRef = ref()
const loading = ref(false)
const validatingToken = ref(false)
const submitMode = ref<'confirm' | 'saveAndContinue'>('confirm')
const showAccessToken = ref(false)

async function handleValidateToken() {
  if (!state.webUrl || !state.platform || !state.accessToken) {
    toast.add({
      title: 'Validation failed',
      description: 'Please fill in Web URL, Platform and Access Token first',
      color: 'warning',
    })
    return
  }
  validatingToken.value = true
  try {
    const result = await repoApi.validateToken({
      webUrl: state.webUrl,
      platform: state.platform,
      accessToken: state.accessToken,
    })
    if (result.valid) {
      message.success('Token is valid')
    } else {
      toast.add({
        title: 'Token invalid',
        description: result.message || 'Token validation failed',
        color: 'error',
      })
    }
  } catch (error: any) {
    toast.add({
      title: 'Validation failed',
      description: error.message || 'Failed to validate token',
      color: 'error',
    })
  } finally {
    validatingToken.value = false
  }
}

const { open, state, resetForm } = useModalForm<RepoCreateParams>({
  name: '',
  webUrl: '',
  platform: 'GITLAB',
  accessToken: '',
})

async function onSubmit(event: FormSubmitEvent<RepoCreateParams>) {
  if (loading.value) return
  loading.value = true

  try {
    const repo = await repoApi.create(event.data)
    message.success('Repository created successfully')

    if (submitMode.value === 'saveAndContinue') {
      state.name = ''
      state.webUrl = ''
      emit('ok', repo)
    } else {
      resetForm()
      open.value = false
      emit('ok', repo)
    }
  } catch (error: any) {
    toast.add({
      title: 'Create failed',
      description: error.message || 'Failed to create repository, please try again',
      color: 'error',
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <UButton label="New Repository" icon="i-lucide-plus" color="primary" @click="open = true" />
  <USlideover
    v-model:open="open"
    :ui="{
      content: 'right-0 inset-y-0 w-[600px] max-w-[96vw]',
      header: 'flex items-center justify-between px-6 py-4',
      body: 'p-6',
      footer: 'flex items-center justify-end gap-3 px-6 py-4',
    }"
  >
    <template #header>
      <h2 class="text-base font-medium">Create Repository</h2>
      <UButton color="neutral" variant="ghost" icon="i-lucide-x" size="md" square @click="open = false" />
    </template>

    <template #body>
      <UForm
        ref="formRef"
        :state="state"
        :schema="repoFormSchema"
        class="space-y-4"
        @submit="onSubmit"
        :validateOn="['input', 'change']"
      >
        <UFormField label="Name" name="name" size="md" :ui="{ label: 'text-sm font-normal mb-1' }">
          <UInput v-model="state.name" placeholder="Enter repository name" size="md" class="w-full" />
        </UFormField>

        <UFormField label="Web URL" name="webUrl" :ui="{ label: 'text-sm font-normal mb-1' }">
          <UInput v-model="state.webUrl" placeholder="https://github.com/user/repo" class="w-full" />
        </UFormField>

        <UFormField label="Platform" name="platform" :ui="{ label: 'text-sm font-normal mb-1' }">
          <USelect v-model="state.platform" :items="PLATFORM_OPTIONS" placeholder="Select platform" />
        </UFormField>

        <UFormField label="Access Token" name="accessToken" :ui="{ label: 'text-sm font-normal mb-1' }">
          <div class="flex gap-2">
            <div class="relative flex-1">
              <UInput
                v-model="state.accessToken"
                :type="showAccessToken ? 'text' : 'password'"
                placeholder="Enter access token (optional)"
                size="md"
                class="w-full"
              />
              <UButton
                :icon="showAccessToken ? 'i-lucide-eye-off' : 'i-lucide-eye'"
                color="neutral"
                variant="ghost"
                size="sm"
                class="absolute right-1 top-1/2 -translate-y-1/2"
                @click="showAccessToken = !showAccessToken"
              />
            </div>
            <UButton
              label="Validate"
              color="neutral"
              variant="outline"
              size="md"
              :loading="validatingToken"
              @click="handleValidateToken"
            />
          </div>
        </UFormField>
      </UForm>
    </template>

    <template #footer>
      <UButton label="Cancel" color="neutral" variant="subtle" @click="open = false" />
      <UButton
        label="Save & Continue"
        color="primary"
        variant="outline"
        :loading="loading"
        @click="submitMode = 'saveAndContinue'; formRef?.submit()"
      />
      <UButton
        label="Confirm"
        color="success"
        variant="solid"
        :loading="loading"
        @click="submitMode = 'confirm'; formRef?.submit()"
      />
    </template>
  </USlideover>
</template>
