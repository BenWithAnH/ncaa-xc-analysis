<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
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
const currentPage = ref(1);
const hasNextPage = ref(true);

const scrapeEtaText = ref('');
const scrapeProgressText = ref('');
let scrapePollInterval = null;

const formatEta = (seconds) => {
  if (seconds === undefined || seconds === null || seconds < 0) return '';
  if (seconds < 60) return `${seconds}s`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  if (m < 60) return `${m}m ${s}s`;
  const h = Math.floor(m / 60);
  const remM = m % 60;
  return `${h}h ${remM}m`;
};

const pollBulkScrapeStatus = async () => {
  try {
    const res = await axios.get('http://localhost:8080/api/meets/bulk-scrape/status');
    const data = res.data;
    if (data.running) {
      if (!scrapeTimer.isLoading.value) {
        scrapeTimer.start();
      }
      if (!scrapePollInterval) {
        scrapePollInterval = setInterval(pollBulkScrapeStatus, 1000);
      }
      if (data.stage === 'INITIAL_COUNT') {
        scrapeEtaText.value = 'Calculating ETA...';
        scrapeProgressText.value = data.message || 'Finding total meets...';
      } else if (data.stage === 'SCRAPING') {
        scrapeEtaText.value = data.etaSeconds > 0 ? formatEta(data.etaSeconds) : 'Calculating ETA...';
        const avgStr = data.avgTimePerMeetMs > 0 ? `${(data.avgTimePerMeetMs / 1000).toFixed(1)}s/meet` : '';
        let progress = `${data.processedMeets}/${data.totalMeets} meets`;
        if (avgStr) progress += ` (~${avgStr})`;
        if (data.currentMeetName) progress += ` - ${data.currentMeetName}`;
        scrapeProgressText.value = progress;
      }
    } else {
      if (scrapePollInterval) {
        clearInterval(scrapePollInterval);
        scrapePollInterval = null;
      }
      if (scrapeTimer.isLoading.value) {
        scrapeTimer.stop();
      }
      scrapeEtaText.value = '';
      scrapeProgressText.value = '';
      if (data.stage === 'COMPLETED') {
        scrapeMsg.value = data.message || 'Bulk load completed.';
      } else if (data.stage === 'FAILED') {
        errorMsg.value = data.error || 'Bulk load failed.';
      } else if (data.stage === 'CANCELLED') {
        scrapeMsg.value = data.message || 'Bulk load cancelled.';
      }
    }
  } catch (err) {
    console.error('Error polling bulk load status:', err);
  }
};

const fetchMeets = async (page = 1) => {
  fetchTimer.start();
  errorMsg.value = '';
  scrapeMsg.value = '';
  try {
    const response = await axios.get(`http://localhost:8080/api/meets?page=${page}`);
    if (page > 1 && (!response.data || response.data.length === 0)) {
      scrapeMsg.value = `No more meets found on page ${page}.`;
      hasNextPage.value = false;
    } else {
      meets.value = response.data;
      currentPage.value = page;
      hasNextPage.value = response.data && response.data.length > 0;
    }
  } catch (error) {
    console.error("Error getting meet list:", error);
    errorMsg.value = 'Failed to load meets.';
  } finally {
    fetchTimer.stop();
  }
};

const nextPage = () => {
  fetchMeets(currentPage.value + 1);
};

const prevPage = () => {
  if (currentPage.value > 1) {
    fetchMeets(currentPage.value - 1);
  }
};

const bulkScrape = async () => {
  scrapeTimer.start();
  errorMsg.value = '';
  scrapeMsg.value = '';
  scrapeEtaText.value = 'Calculating ETA...';
  scrapeProgressText.value = 'Initializing search...';
  try {
    await axios.post(`http://localhost:8080/api/meets/bulk-scrape-since-year?startYear=${startYear.value}&maxPages=200`);
    if (scrapePollInterval) clearInterval(scrapePollInterval);
    scrapePollInterval = setInterval(pollBulkScrapeStatus, 1000);
    pollBulkScrapeStatus();
  } catch (error) {
    console.error("Error starting bulk load:", error);
    errorMsg.value = 'Failed to start bulk load.';
    scrapeTimer.stop();
    scrapeEtaText.value = '';
    scrapeProgressText.value = '';
  }
};

const cancelBulkScrape = async () => {
  try {
    await axios.post('http://localhost:8080/api/meets/bulk-scrape/cancel');
  } catch (err) {
    console.error('Error cancelling bulk scrape:', err);
  }
};

onMounted(() => {
  fetchMeets(1);
  pollBulkScrapeStatus();
});

onUnmounted(() => {
  if (scrapePollInterval) {
    clearInterval(scrapePollInterval);
  }
});
</script>

<template>
  <div class="meet-list-container">
    <header>
      <h2>XC Meets</h2>
      <div class="actions">
        <button @click="fetchMeets(1)" :disabled="isLoading">
          {{ fetchTimer.isLoading.value ? `Loading (${fetchTimer.elapsedSeconds.value}s)...` : 'Load Meet List' }}
        </button>
        <div class="scrape-actions">
          <input type="number" v-model="startYear" class="year-input" placeholder="Year (e.g. 2023)" />
          <button @click="bulkScrape" :disabled="isLoading">
            {{ scrapeTimer.isLoading.value ? `Scraping (${scrapeTimer.elapsedSeconds.value}s${scrapeEtaText ? ' | ETA: ' + scrapeEtaText : ''})...` : 'Bulk Scrape' }}
          </button>
          <button v-if="scrapeTimer.isLoading.value" @click="cancelBulkScrape" class="cancel-btn" title="Cancel bulk scrape">
            ✕ Cancel
          </button>
        </div>
      </div>
    </header>

    <div v-if="fetchTimer.isLoading.value" class="status-loading">Loading meets (for {{ fetchTimer.elapsedSeconds.value }}s)...</div>
    <div v-if="scrapeTimer.isLoading.value" class="status-loading">
      Bulk loading meets since {{ startYear }} (for {{ scrapeTimer.elapsedSeconds.value }}s<span v-if="scrapeEtaText"> | ETA: {{ scrapeEtaText }}</span><span v-if="scrapeProgressText"> - {{ scrapeProgressText }}</span>)...
    </div>
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
            <button @click="emit('view-meet', meet)">View</button>
        </td>
        </tr>
      </tbody>
    </table>

    <div v-if="meets.length" class="pagination-controls">
      <button 
        @click="prevPage" 
        :disabled="currentPage <= 1 || isLoading"
        class="page-btn"
      >
        &larr; Previous Page
      </button>
      <span class="page-indicator">Page {{ currentPage }}</span>
      <button 
        @click="nextPage" 
        :disabled="isLoading || !hasNextPage"
        class="page-btn"
      >
        Next Page &rarr;
      </button>
    </div>

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
.pagination-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 1rem;
  padding: 0.75rem 0;
  border-top: 1px solid #e5e7eb;
}
.page-btn {
  padding: 0.4rem 0.8rem;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid #ccc;
  border-radius: 4px;
  background-color: #fff;
  transition: all 0.15s ease;
}
.page-btn:hover:not(:disabled) {
  background-color: #f3f4f6;
  border-color: #9ca3af;
}
.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.page-indicator {
  font-size: 0.875rem;
  font-weight: 600;
  color: #374151;
}
.cancel-btn {
  background-color: #fee2e2;
  color: #b91c1c;
  border: 1px solid #fca5a5;
  border-radius: 4px;
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}
.cancel-btn:hover {
  background-color: #fecaca;
  color: #991b1b;
}
</style>