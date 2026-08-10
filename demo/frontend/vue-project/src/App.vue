<script setup>
import RunnerTable from './components/RunnerTable.vue'
import MeetList from './components/MeetList.vue'
import TopAthletesList from './components/TopAthletesList.vue'
import { ref } from 'vue';

const selectedUrl = ref('')

const handleMeetSelection = (url) => {
  selectedUrl.value = url;
};
</script>

<template>
  <div class="layout-container">
    <div class="left">
      <MeetList @view-meet="handleMeetSelection"/>
    </div>
    
    <div class="middle">
      <RunnerTable v-if="selectedUrl" :targetMeetUrl="selectedUrl"/>
      <div v-else class ="base">
        <p>Select a meet from the list to view its data.</p>
      </div>
    </div>

    <div class="right">
      <TopAthletesList />
    </div>
  </div>
</template>

<style scoped>
.layout-container {
  display: flex;
  gap: 2rem;
  padding: 2rem;
  align-items: flex-start;
}

.left{
  flex: 3;
}

.middle{
  flex: 4;
  padding: 0 1rem;
}

.right{
  flex: 3;
}

.base{
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}
</style>