<script setup>
import { ref, watch } from 'vue';
import axios from 'axios';
import { useLoadingTimer } from '../composables/useLoadingTimer';

const props = defineProps({
  athlete: {
    type: Object,
    required: true
  }
});

const emit = defineEmits(['close']);

const raceResults = ref([]);
const errorMsg = ref('');
const { isLoading, elapsedSeconds, start, stop } = useLoadingTimer();

const fetchAthleteResults = async () => {
  if (!props.athlete?.link) {
    raceResults.value = [];
    return;
  }

  start();
  errorMsg.value = '';
  raceResults.value = [];
  try {
    const response = await axios.get(`http://localhost:8080/api/athletes/results?link=${encodeURIComponent(props.athlete.link)}`);
    raceResults.value = response.data || [];
  } catch (error) {
    console.error('Error fetching athlete race results:', error);
    errorMsg.value = 'Failed to load athlete race records.';
  } finally {
    stop();
  }
};

watch(() => props.athlete, () => {
  if (props.athlete) {
    fetchAthleteResults();
  }
}, { immediate: true, deep: true });
</script>

<template>
  <div class="athlete-records-card">
    <header class="card-header">
      <div class="header-left">
        <h2>{{ athlete.name }}</h2>
        <div class="athlete-meta">
          <span v-if="athlete.rating" class="meta-item">
            <strong>Rating:</strong> {{ athlete.rating.toFixed(2) }}
          </span>
          <span v-if="athlete.bestTime" class="meta-item">
            <strong>Best Time:</strong> {{ athlete.bestTime }}
          </span>
          <span class="meta-item">
            <strong>Stored Races:</strong> {{ raceResults.length }}
          </span>
          <a
            v-if="athlete.link"
            :href="athlete.link"
            target="_blank"
            rel="noopener noreferrer"
            class="tfrrs-link"
          >
            TFRRS Profile ↗
          </a>
        </div>
      </div>
      <button @click="emit('close')" class="close-btn" type="button" aria-label="Close athlete records">
        ✕ Close
      </button>
    </header>

    <div v-if="isLoading" class="status-loading">
      Loading athlete records (for {{ elapsedSeconds }}s)...
    </div>
    <div v-if="errorMsg" class="error">{{ errorMsg }}</div>

    <table v-if="raceResults.length" class="results-table">
      <thead>
        <tr>
          <th scope="col">Meet Name</th>
          <th scope="col">Date</th>
          <th scope="col">Time</th>
          <th scope="col">Prior Rating</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="res in raceResults" :key="res.id || (res.meetName + res.raceDate)">
          <td>{{ res.meetName || 'Unknown Meet' }}</td>
          <td>{{ res.raceDate || 'TBD' }}</td>
          <td>{{ res.raceTime || 'N/A' }}</td>
          <td>{{ res.priorRating != null ? res.priorRating.toFixed(2) : 'N/A' }}</td>
        </tr>
      </tbody>
    </table>

    <p v-else-if="!isLoading && raceResults.length === 0" class="empty-state">
      No historical race records stored in the database for this athlete yet.
    </p>
  </div>
</template>

<style scoped>
.athlete-records-card {
  padding: 1rem;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.25rem;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 0.75rem;
}

.card-header h2 {
  margin: 0 0 0.4rem 0;
  color: #111827;
}

.athlete-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  font-size: 0.9rem;
  color: #4b5563;
  align-items: center;
}

.tfrrs-link {
  color: #42b983;
  font-weight: 500;
  text-decoration: none;
}

.tfrrs-link:hover {
  text-decoration: underline;
}

.close-btn {
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 0.35rem 0.75rem;
  font-size: 0.85rem;
  cursor: pointer;
  color: #374151;
  transition: background-color 0.15s;
}

.close-btn:hover {
  background: #e5e7eb;
}

.results-table {
  width: 100%;
  border-collapse: collapse;
}

.results-table th,
.results-table td {
  border: 1px solid #ccc;
  padding: 0.5rem;
  text-align: left;
}

.status-loading {
  color: #666;
  font-style: italic;
  margin-bottom: 1rem;
}

.error {
  color: red;
  margin-bottom: 1rem;
}

.empty-state {
  color: #666;
  font-style: italic;
  padding: 1rem 0;
}
</style>
