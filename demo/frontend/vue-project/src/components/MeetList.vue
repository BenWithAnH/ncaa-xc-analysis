<script setup>
import { ref, computed } from 'vue';
import axios from 'axios';
import { useLoadingTimer } from '../composables/useLoadingTimer';

const meets = ref([]);
const fetchTimer = useLoadingTimer();
const scrapeTimer = useLoadingTimer();
const isLoading = computed(() => fetchTimer.isLoading.value || scrapeTimer.isLoading.value);

const errorMsg = ref('');
const scrapeMsg = ref('');
const emit = defineEmits(['view-meet']);
const startYear = ref(2026); // Default year

const fetchMeets = async () => {
  fetchTimer.start();
  errorMsg.value = '';
  scrapeMsg.value = '';
  try {
    const response = await axios.get('http://localhost:8080/api/meets?maxPages=1');
    meets.value = response.data;
  } catch (error) {
    console.error("Error getting meet list:", error);
    errorMsg.value = 'Failed to load meets.';
  } finally {
    fetchTimer.stop();
  }
};

const bulkScrape = async () => {
  scrapeTimer.start();
  errorMsg.value = '';
  scrapeMsg.value = '';
  try {
    const response = await axios.post(`http://localhost:8080/api/meets/bulk-scrape-since-year?startYear=${startYear.value}&maxPages=200`);
    scrapeMsg.value = response.data;
  } catch (error) {
    console.error("Error bulk scraping:", error);
    errorMsg.value = 'Failed to bulk scrape meets.';
  } finally {
    scrapeTimer.stop();
  }
};
</script>

<template>
  <div class="meet-list-container">
    <header>
      <h2>XC Meets</h2>
      <div class="actions">
        <button @click="fetchMeets" :disabled="isLoading">
          {{ fetchTimer.isLoading.value ? `Loading (${fetchTimer.elapsedSeconds.value}s)...` : 'Load Meet List' }}
        </button>
        <div class="scrape-actions">
          <input type="number" v-model="startYear" class="year-input" placeholder="Year (e.g. 2023)" />
          <button @click="bulkScrape" :disabled="isLoading">
            {{ scrapeTimer.isLoading.value ? `Scraping (${scrapeTimer.elapsedSeconds.value}s)...` : 'Bulk Scrape' }}
          </button>
        </div>
      </div>
    </header>

    <div v-if="fetchTimer.isLoading.value" class="status-loading">Loading meets (for {{ fetchTimer.elapsedSeconds.value }}s)...</div>
    <div v-if="scrapeTimer.isLoading.value" class="status-loading">Bulk scraping meets since {{ startYear }} (for {{ scrapeTimer.elapsedSeconds.value }}s)...</div>
    <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
    <div v-if="scrapeMsg" class="success">{{ scrapeMsg }}</div>
    
    <table v-if="meets.length" class="meet-table">
      <thead>
        <tr>
          <th scope="col">Name</th>
          <th scope="col">Date</th>
          <th scope="col">Link</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="meet in meets" :key="meet.url">
        <td>{{ meet.name }}</td>
        <td>{{ meet.date }}</td>
        <td>
            <button @click="emit('view-meet', meet.url)">View</button>
        </td>
        </tr>
      </tbody>
    </table>

    <p v-else-if="!isLoading && meets.length === 0" class="empty-state text-muted">
      No meets loaded yet. Click the button to load meets.
    </p>
  </div>
</template>

<style scoped>
.meet-list-container {
  padding: 1rem;
}
header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}
.actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.scrape-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-left: 1px solid #ccc;
  padding-left: 1rem;
}
.year-input {
  width: 80px;
  padding: 0.2rem;
}
.status-loading {
  color: #666;
  font-style: italic;
  margin-bottom: 1rem;
}
.meet-table {
  width: 100%;
  border-collapse: collapse;
}
.meet-table th, .meet-table td {
  border: 1px solid #ccc;
  padding: 0.5rem;
  text-align: left;
}
.error {
  color: red;
  margin-bottom: 1rem;
}
.success {
  color: green;
  margin-bottom: 1rem;
}
</style>