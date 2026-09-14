<script setup>
import { ref } from 'vue';
import axios from 'axios';
import { useLoadingTimer } from '../composables/useLoadingTimer';

const athletes = ref([]);
const { isLoading, elapsedSeconds, start, stop } = useLoadingTimer();
const errorMsg = ref('');

const fetchTopAthletes = async () => {
  start();
  errorMsg.value = '';
  try {
    const response = await axios.get('http://localhost:8080/api/athletes/top');
    athletes.value = response.data;
  } catch (error) {
    console.error("Error getting top athletes:", error);
    errorMsg.value = 'Failed to load top athletes.';
  } finally {
    stop();
  }
};
</script>

<template>
  <div class="top-athletes-container">
    <header>
      <h2>Top Athletes</h2>
      <button @click="fetchTopAthletes" :disabled="isLoading">
        {{ isLoading ? `Loading (${elapsedSeconds}s)...` : 'Load Top Athletes' }}
      </button>
    </header>

    <div v-if="isLoading" class="status-loading">Loading top athletes (for {{ elapsedSeconds }}s)...</div>
    <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
    
    <table v-if="athletes.length" class="athlete-table">
      <thead>
        <tr>
          <th scope="col">Name</th>
          <th scope="col">Rating</th>
          <th scope="col">Best Time</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="athlete in athletes" :key="athlete.link">
          <td><a :href="athlete.link" target="_blank">{{ athlete.name }}</a></td>
          <td>{{ athlete.rating ? athlete.rating.toFixed(2) : 'N/A' }}</td>
          <td>{{ athlete.bestTime || 'N/A' }}</td>
        </tr>
      </tbody>
    </table>

    <p v-else-if="!isLoading && athletes.length === 0" class="empty-state text-muted">
      No top athletes loaded yet. Click the button to load them.
    </p>
  </div>
</template>

<style scoped>
.top-athletes-container {
  padding: 1rem;
}
header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}
.status-loading {
  color: #666;
  font-style: italic;
  margin-bottom: 1rem;
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
.error {
  color: red;
  margin-bottom: 1rem;
}
a {
  color: #42b983;
  text-decoration: none;
}
a:hover {
  text-decoration: underline;
}
</style>
