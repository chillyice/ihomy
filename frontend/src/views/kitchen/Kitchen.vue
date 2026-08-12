<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('kitchen.title') }]" />
    <div class="card">
      <h2>{{ $t('kitchen.title') }}</h2>
      <el-empty :description="$t('kitchen.placeholder')" />
    </div>
  </div>
</template>

<script setup>
import Breadcrumb from '@/components/Breadcrumb.vue'
</script>
