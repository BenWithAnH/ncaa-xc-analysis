<script setup>
import { ref } from 'vue';
import axios from 'axios';

const meets = ref([]);
const isLoading = ref(false);
const errorMsg = ref('');
const emit = defineEmits(['view-meet']);

const fetchMeets = async () => {
  isLoading.value = true;
  errorMsg.value = '';
  try {
    const response = await axios.get('http://localhost:8080/api/meets?maxPages=1');
    meets.value = response.data;
  } catch (error) {
    console.error("Error getting meet list:", error);
    errorMsg.value = 'Failed to load meets.';
  } finally {
    isLoading.value = false;
  }
};
</script>

<template>
  <div class="meet-list-container">
    <header>
      <h2>XC Meets</h2>
      <button @click="fetchMeets" :disabled="isLoading">
        {{ isLoading ? 'Loading...' : 'Load Meet List' }}
      </button>
    </header>

    <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
    
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
</style>