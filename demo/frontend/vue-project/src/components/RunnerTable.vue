<script setup>
import { ref, watch } from 'vue';
import axios from 'axios';
import { useLoadingTimer } from '../composables/useLoadingTimer';

const raceData = ref(null);
const errorMsg = ref('');
const { isLoading, elapsedSeconds, start, stop } = useLoadingTimer();

const emit = defineEmits(['race-rated']);

const props = defineProps({
  targetMeet: {
    type: [Object, String],
    required: true
  }
});

const scrapeRace = async () => {
  const meetUrl = typeof props.targetMeet === 'object' ? props.targetMeet?.url : props.targetMeet;
  if (!meetUrl) return;

  start();
  errorMsg.value = '';
  raceData.value = null;
  try {
    const payload = {
      meetUrl: meetUrl,
      meetName: typeof props.targetMeet === 'object' ? (props.targetMeet?.name || '') : '',
      meetDate: typeof props.targetMeet === 'object' ? (props.targetMeet?.date || '') : ''
    };
    
    const response = await axios.post('http://localhost:8080/api/race/rate', payload);
    raceData.value = response.data;
    emit('race-rated', response.data);
  } catch (error) {
    console.error("Error getting race:", error);
    errorMsg.value = error.response?.data?.message || 'Failed to load meet data.';
  } finally {
    stop();
  }
};

watch(() => props.targetMeet, () => {
  if (props.targetMeet) {
    scrapeRace();
  }
}, { immediate: true, deep: true });

</script>

<template>
  <div>
    <div v-if="isLoading" class="text-muted status-loading">Loading race data (for {{ elapsedSeconds }}s)...</div>
    <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
    
    <section v-if="raceData" class="results-card" aria-labelledby="meet-analysis-title">
      <header>
        <h2 id="meet-analysis-title">Meet Analysis</h2>
        <div class="race-metrics">
          <p><strong>Distance:</strong> {{ raceData.distance }} meters</p>
          <p><strong>Course Adjustment Factor (CAF):</strong> {{ raceData.caf }}</p>
        </div>
      </header>

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

      <p v-else class="empty-state text-muted">
        No athlete data found for this meet.
      </p>
    </section>
  </div>
</template>

<style scoped>
.results-card {
  padding: 1rem;
}
header {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
header h2 {
  margin: 0;
}
.race-metrics {
  display: flex;
  gap: 1rem;
  font-size: 0.9rem;
  color: #666;
}
.race-metrics p {
  margin: 0;
}
.athlete-table {
  width: 100%;
  border-collapse: collapse;
}
.athlete-table th, .athlete-table td {
  border: 1px solid #ccc;
  padding: 0.5rem;
  text-align: left;
}
a {
  color: #42b983;
  text-decoration: none;
}
a:hover {
  text-decoration: underline;
}
.text-muted {
  color: #666;
}
.status-loading {
  font-style: italic;
  margin-bottom: 1rem;
}
.error {
  color: red;
  margin-bottom: 1rem;
}
</style>
