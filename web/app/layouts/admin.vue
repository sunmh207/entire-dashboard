<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

const route = useRoute()
const open = ref(true) // Expanded by default

// Menu items configuration
const items: NavigationMenuItem[] = [
  {
    label: 'Overview',
    icon: 'i-lucide-layout-dashboard',
    to: '/admin/overview',
    onSelect: () => {
      // Close sidebar after clicking on mobile
      if (import.meta.client && window.innerWidth < 1024) {
        open.value = false
      }
    },
  },
  {
    label: 'Repositories',
    icon: 'i-lucide-folder-git',
    to: '/admin/repositories',
    exact: false, // Allow sub-paths to also match this menu item
    onSelect: () => {
      if (import.meta.client && window.innerWidth < 1024) {
        open.value = false
      }
    },
  },
  {
    label: 'Checkpoints',
    icon: 'i-lucide-git-commit',
    to: '/admin/checkpoints',
    exact: false,
    onSelect: () => {
      if (import.meta.client && window.innerWidth < 1024) {
        open.value = false
      }
    },
  },
]

// Listen to route changes, auto close sidebar on mobile
watch(() => route.path, () => {
  if (import.meta.client && window.innerWidth < 1024) {
    open.value = false
  }
})
</script>

<template>
  <UDashboardGroup unit="rem">
    <UDashboardSidebar v-model:open="open" collapsible resizable>
      <template #header="{ collapsed }">
        <div class="flex justify-between items-center px-0.5 py-1.5 w-full">
          <div class="flex items-center gap-2 min-w-0">
            <UIcon 
              name="i-lucide-code" 
              class="w-6 h-6 text-success flex-shrink-0"
            />
            <Transition
              enter-active-class="transition-opacity duration-200"
              leave-active-class="transition-opacity duration-150"
              enter-from-class="opacity-0"
              leave-to-class="opacity-0"
            >
              <span 
                v-show="!collapsed" 
                class="text-lg font-medium text-highlighted  whitespace-nowrap"
              >
                Entire Dashboard
              </span>
            </Transition>
          </div>
        </div>
      </template>

      <template #default="{ collapsed }">
        <UNavigationMenu :collapsed="collapsed" tooltip :items="items" orientation="vertical" />
      </template>

      <template #footer="{ collapsed }">
        <AdminUserMenu :collapsed="collapsed" />
      </template>
    </UDashboardSidebar>

    <slot />
  </UDashboardGroup>
</template>

