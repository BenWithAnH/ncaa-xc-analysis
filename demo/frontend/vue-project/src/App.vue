<script setup>
import RunnerTable from './components/RunnerTable.vue'
import MeetList from './components/MeetList.vue'
import TopAthletesList from './components/TopAthletesList.vue'
import SearchBar from './components/SearchBar.vue'
import AthleteRecords from './components/AthleteRecords.vue'
import { ref } from 'vue';

const selectedMeet = ref(null);
const selectedAthlete = ref(null);
const topAthletesRef = ref(null);

const handleMeetSelection = (meet) => {
  selectedMeet.value = meet;
  selectedAthlete.value = null;
};

const handleAthleteSelection = (athlete) => {
  selectedAthlete.value = athlete;
};

const handleCloseAthlete = () => {
  selectedAthlete.value = null;
};

const handleRaceRated = () => {
  if (topAthletesRef.value?.fetchTopAthletes) {
    topAthletesRef.value.fetchTopAthletes();
  }
};
</script>

<template>
  <div class="app-root">
    <header class="app-header">
      <div class="search-section">
        <SearchBar @select-athlete="handleAthleteSelection" />
      </div>
    </header>

    <div class="layout-container">
      <div class="left">
        <MeetList @view-meet="handleMeetSelection"/>
      </div>
      
      <div class="middle">
        <AthleteRecords
          v-if="selectedAthlete"
          :athlete="selectedAthlete"
          @close="handleCloseAthlete"
        />
        <RunnerTable 
          v-else-if="selectedMeet" 
          :targetMeet="selectedMeet" 
          @race-rated="handleRaceRated"
        />
        <div v-else class="base">
          <p>Select a meet from the list or search for an athlete above.</p>
        </div>
      </div>

      <div class="right">
        <TopAthletesList ref="topAthletesRef" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-root {
  width: 100%;
}

.app-header {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 1.5rem 2rem 0.5rem 2rem;
}

.search-section {
  width: 100%;
  max-width: 500px;
}

.layout-container {
  display: flex;
  gap: 2rem;
  padding: 1.5rem 2rem 2rem 2rem;
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
  color: #6b7280;
}
</style>