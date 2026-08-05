<script setup>
import { ref, watch } from 'vue';
import axios from 'axios';

const raceData = ref(null);
const isLoading = ref(false);

const props = defineProps({
  targetMeetUrl: {
    type: String,
    required: true
  }
});

const scrapeRace = async () => {
  if(!props.targetMeetUrl) return;

  isLoading.value = true;
  try {
    const payload = {
      meetUrl: props.targetMeetUrl
    };
    
    const response = await axios.post('http://localhost:8080/api/race/rate', payload);
    
    raceData.value = response.data;
  } catch (error) {
    console.error("Error scraping race:", error);
  } finally {
    isLoading.value = false;
  }
};

watch(() => props.targetMeetUrl, () => {
  if (props.targetMeetUrl) {
    scrapeRace();
  }
}, { immediate: true });

</script>

<template>
  <div>
    <div v-if="isLoading" class="text-muted">Loading Race Data...</div>
    
    <!-- Displays only after raceData is fetched -->
    <section v-if="raceData" class="results-card" aria-labelledby="meet-analysis-title">
      <header>
        <h2 id="meet-analysis-title">Meet Analysis</h2>
        <div class="race-metrics">
          <p><strong>Distance:</strong> {{ raceData.distance }} meters</p>
          <p><strong>Course Adjustment Factor (CAF):</strong> {{ raceData.caf }}</p>
        </div>
      </header>

      <!-- Athlete Table: Checks if athletes array exists and has items -->
      <table v-if="raceData.athletes?.length" class="athlete-table">
        <caption class="sr-only">Meet Athlete Results</caption>
        <thead>
          <tr>
            <th scope="col">Runner</th>
            <th scope="col">Time</th>
            <th scope="col">Rating</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="athlete in raceData.athletes" :key="athlete.link">
            <td>
              <a 
                :href="athlete.link" 
                target="_blank" 
                rel="noopener noreferrer"
              >
                {{ athlete.name }}
              </a>
            </td>
            <td>{{ athlete.time }}</td>
            <td>{{ athlete.rating }}</td>
          </tr>
        </tbody>
      </table>

      <!-- Empty State: Displays if the athletes array is empty -->
      <p v-else class="empty-state text-muted">
        No athlete data found for this meet.
      </p>
    </section>
  </div>
</template>
