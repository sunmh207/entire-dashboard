<script setup lang="ts">
import type { ParsedTranscript, MessageView } from '../utils/transcript-parser'

const props = defineProps<{
  parsed: ParsedTranscript
}>()

function formatTime(ms?: number): string {
  if (ms == null) return ''
  const d = new Date(ms)
  return d.toLocaleTimeString()
}
</script>

<template>
  <div class="space-y-4">
    <div v-for="(msg, i) in (props.parsed.messages ?? [])" :key="msg.id || i" class="space-y-2">
      <!-- User message -->
      <div
        v-if="msg.role === 'user'"
        class="flex gap-3"
      >
        <div class="shrink-0 w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center">
          <UIcon name="i-lucide-user" class="w-4 h-4 text-primary" />
        </div>
        <div class="flex-1 min-w-0">
          <div v-if="msg.taskTitle" class="text-sm font-medium text-default mb-1">
            {{ msg.taskTitle }}
          </div>
          <div class="text-sm text-default whitespace-pre-wrap break-words">
            {{ msg.text }}
          </div>
        </div>
      </div>

      <!-- Assistant message -->
      <div
        v-else
        class="flex gap-3"
      >
        <div class="shrink-0 w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center">
          <UIcon name="i-lucide-bot" class="w-4 h-4 text-blue-600 dark:text-blue-400" />
        </div>
        <div class="flex-1 min-w-0 space-y-2">
          <div v-if="msg.reasoning" class="text-xs text-gray-500 italic">
            {{ msg.reasoning }}
          </div>
          <div v-if="msg.text" class="text-sm text-default whitespace-pre-wrap break-words">
            {{ msg.text }}
          </div>
          <div v-if="(msg.toolsCount ?? 0) > 0" class="flex items-center gap-2">
            <UButton
              size="xs"
              color="neutral"
              variant="soft"
              :icon="'i-lucide-wrench'"
            >
              {{ msg.toolsCount }} tool{{ (msg.toolsCount ?? 0) > 1 ? 's' : '' }} used
            </UButton>
            <div class="flex flex-wrap gap-1">
              <UBadge
                v-for="(t, ti) in (msg.tools ?? [])"
                :key="t.callID || `tool-${ti}`"
                size="xs"
                variant="subtle"
                color="neutral"
              >
                {{ t.tool }}
              </UBadge>
            </div>
          </div>
          <div v-if="(msg.tools ?? []).length > 0" class="text-xs space-y-1 mt-2">
            <details
              v-for="(t, ti) in (msg.tools ?? [])"
              :key="t.callID || `toold-${ti}`"
              class="border border-gray-200 dark:border-gray-700 rounded px-2 py-1"
            >
              <summary class="cursor-pointer font-mono text-gray-600 dark:text-gray-400">
                {{ t.tool }}({{ t.title ? `"${t.title}"` : '...' }})
              </summary>
              <pre class="text-xs mt-1 overflow-auto max-h-32 text-gray-500">{{ JSON.stringify(t.input, null, 2) }}</pre>
            </details>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
