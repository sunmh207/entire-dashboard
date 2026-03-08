<script setup lang="ts">
import RepoList from '~/features/repository/components/RepoList.vue'

definePageMeta({
  layout: 'admin',
})
</script>

<template>
  <UDashboardPanel id="dashboard">
    <template #header>
      <UDashboardNavbar title="Repositories">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>
    <template #body>
      <RepoList />
    </template>
  </UDashboardPanel>
</template>
