<script setup lang="ts">
import { createUnifiedDiff } from '../utils/format-diff'
import type { ParsedTranscript } from '../utils/transcript-parser'

const props = defineProps<{
  parsed: ParsedTranscript
  file: string
}>()

const diffContent = computed(() => {
  const messagesWithFile = (props.parsed.messages ?? []).filter((m) =>
    m.diffs?.some((d) => d.file === props.file)
  )
  const lastMsg = messagesWithFile[messagesWithFile.length - 1]
  const d = lastMsg?.diffs?.find((x) => x.file === props.file)
  if (!d) return ''
  return createUnifiedDiff(props.file, d.before ?? '', d.after ?? '')
})

const diffLines = computed(() => {
  const content = diffContent.value
  if (!content) return []
  return content.split(/\r?\n/).map((line) => ({
    type: line.startsWith('-') && !line.startsWith('---') ? 'del' : line.startsWith('+') && !line.startsWith('+++') ? 'add' : 'ctx',
    text: line,
  }))
})
</script>

<template>
  <div class="font-mono text-sm">
    <div class="text-xs text-gray-500 mb-2 truncate">{{ file }}</div>
    <div class="rounded border border-gray-200 dark:border-gray-700 overflow-hidden">
      <div
        v-for="(line, i) in diffLines"
        :key="i"
        class="px-3 py-0.5 whitespace-pre"
        :class="{
          'bg-red-500/10': line.type === 'del',
          'bg-green-500/10': line.type === 'add',
          'text-red-600 dark:text-red-400': line.type === 'del',
          'text-green-600 dark:text-green-400': line.type === 'add',
        }"
      >
        {{ line.text }}
      </div>
    </div>
  </div>
</template>
