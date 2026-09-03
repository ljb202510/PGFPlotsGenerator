<template>
  <header class="page-header">
    <div class="page-header__main">
      <h2 v-if="title" class="page-header__title">{{ title }}</h2>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
      <slot name="subtitle" />
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script>
export default {
  name: 'PageHeader',
  props: {
    title: { type: String, default: '' },
    description: { type: String, default: '' }
  }
}
</script>
