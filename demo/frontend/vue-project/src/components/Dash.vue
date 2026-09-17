<!-- eslint-disable vue/multi-word-component-names -->
<script setup>
import { ref } from 'vue';
import MeetList from './MeetList.vue';
import RunnerTable from './RunnerTable.vue';

const selectedUrl = ref('');

const handleMeetSelection = (url) => {
  selectedUrl.value = url;
};
</script>

<template>
  <div class="dashboard">
    <MeetList @view-meet="handleMeetSelection" />
    
    <hr />
    
    <RunnerTable v-if="selectedUrl" :targetMeetUrl="selectedUrl" />
  </div>
</template>