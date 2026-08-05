<script setup>
import RunnerTable from './components/RunnerTable.vue'
import MeetList from './components/MeetList.vue'
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
    <hr style="margin: 2rem 0;" />
    <div class="right">
      <RunnerTable v-if="selectedUrl" :targetMeetUrl="selectedUrl"/>
      <div v-else class ="base">
        <p>Select a meet from the list to view its data.</p>
      </div>
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
  flex: 6;
  /*max-width: 400px; */
}

.right{
  flex: 3;
}

.base{
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
  background-color: rgb(102, 103, 111);
  border: 2px dashed #fefbfb;
  color: rgba(5, 5, 5, 0.6);
}

header {
  line-height: 1.5;
}

.logo {
  display: block;
  margin: 0 auto 2rem;
}

@media (min-width: 1024px) {
  header {
    display: flex;
    place-items: center;
    padding-right: calc(var(--section-gap) / 2);
  }

  .logo {
    margin: 0 2rem 0 0;
  }

  header .wrapper {
    display: flex;
    place-items: flex-start;
    flex-wrap: wrap;
  }
}
</style>