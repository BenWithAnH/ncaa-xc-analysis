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
    <!-- Listen for the emitted event -->
    <MeetList @view-meet="handleMeetSelection" />
    
    <hr />
    
    <!-- Pass the selected URL into the RunnerTable -->
    <!-- We use v-if so it only renders once a meet is selected -->
    <RunnerTable v-if="selectedUrl" :targetMeetUrl="selectedUrl" />
  </div>
</template>