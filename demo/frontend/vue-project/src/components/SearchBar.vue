<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue';
import axios from 'axios';

const emit = defineEmits(['select-athlete']);

const query = ref('');
const results = ref([]);
const isOpen = ref(false);
const isLoading = ref(false);
const selectedIndex = ref(-1);
const searchContainerRef = ref(null);
let debounceTimeout = null;

const searchAthletes = async (searchTerm) => {
  if (!searchTerm || !searchTerm.trim()) {
    results.value = [];
    isOpen.value = false;
    isLoading.value = false;
    return;
  }

  isLoading.value = true;
  isOpen.value = true;
  try {
    const response = await axios.get(`http://localhost:8080/api/athletes/search?query=${encodeURIComponent(searchTerm.trim())}`);
    results.value = response.data || [];
  } catch (error) {
    console.error('Error searching athletes:', error);
    results.value = [];
  } finally {
    isLoading.value = false;
    selectedIndex.value = -1;
  }
};

watch(query, (newVal) => {
  if (debounceTimeout) clearTimeout(debounceTimeout);
  if (!newVal || !newVal.trim()) {
    results.value = [];
    isOpen.value = false;
    isLoading.value = false;
    return;
  }

  debounceTimeout = setTimeout(() => {
    searchAthletes(newVal);
  }, 250);
});

const handleSelect = (athlete) => {
  if (!athlete) return;
  query.value = athlete.name;
  isOpen.value = false;
  emit('select-athlete', athlete);
};

const handleKeydown = (e) => {
  if (!isOpen.value && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
    isOpen.value = true;
    return;
  }

  if (e.key === 'ArrowDown') {
    e.preventDefault();
    if (results.value.length > 0) {
      selectedIndex.value = (selectedIndex.value + 1) % results.value.length;
    }
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    if (results.value.length > 0) {
      selectedIndex.value = (selectedIndex.value - 1 + results.value.length) % results.value.length;
    }
  } else if (e.key === 'Enter') {
    e.preventDefault();
    if (selectedIndex.value >= 0 && selectedIndex.value < results.value.length) {
      handleSelect(results.value[selectedIndex.value]);
    } else if (results.value.length === 1) {
      handleSelect(results.value[0]);
    }
  } else if (e.key === 'Escape') {
    isOpen.value = false;
  }
};

const handleClear = () => {
  query.value = '';
  results.value = [];
  isOpen.value = false;
};

const handleClickOutside = (e) => {
  if (searchContainerRef.value && !searchContainerRef.value.contains(e.target)) {
    isOpen.value = false;
  }
};

onMounted(() => {
  document.addEventListener('click', handleClickOutside);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside);
  if (debounceTimeout) clearTimeout(debounceTimeout);
});
</script>

<template>
  <div class="search-bar-wrapper" ref="searchContainerRef">
    <div class="search-input-container">
      <span class="search-icon">🔍</span>
      <input
        type="text"
        v-model="query"
        @focus="query.trim() ? isOpen = true : null"
        @keydown="handleKeydown"
        placeholder="Search for an athlete..."
        class="search-input"
        aria-label="Search athlete"
        autocomplete="off"
      />
      <button v-if="query" @click="handleClear" class="clear-btn" type="button" aria-label="Clear search">
        ✕
      </button>
    </div>

    <!-- Autocomplete Dropdown -->
    <div v-if="isOpen && query.trim()" class="dropdown-menu">
      <div v-if="isLoading" class="dropdown-item loading-item">
        <span>Searching...</span>
      </div>

      <template v-else-if="results.length > 0">
        <div
          v-for="(athlete, index) in results"
          :key="athlete.link || athlete.name"
          :class="['dropdown-item', { 'highlighted': index === selectedIndex }]"
          @mousedown.prevent="handleSelect(athlete)"
        >
          <div class="athlete-main">
            <span class="athlete-name">{{ athlete.name }}</span>
            <span v-if="athlete.rating" class="badge badge-rating">{{ athlete.rating.toFixed(1) }}</span>
          </div>
          <span v-if="athlete.bestTime" class="athlete-sub">Best: {{ athlete.bestTime }}</span>
        </div>
      </template>

      <div v-else-if="!isLoading" class="dropdown-item empty-item">
        <span>No athlete found for "{{ query }}"</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-bar-wrapper {
  position: relative;
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
}

.search-input-container {
  display: flex;
  align-items: center;
  background: #ffffff;
  border: 1.5px solid #d1d5db;
  border-radius: 24px;
  padding: 0.5rem 1rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-input-container:focus-within {
  border-color: #42b983;
  box-shadow: 0 0 0 3px rgba(66, 185, 131, 0.15);
}

.search-icon {
  font-size: 0.95rem;
  margin-right: 0.5rem;
  opacity: 0.6;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 0.95rem;
  color: #1f2937;
  background: transparent;
}

.search-input::placeholder {
  color: #9ca3af;
}

.clear-btn {
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 0.85rem;
  cursor: pointer;
  padding: 0 0.25rem;
}

.clear-btn:hover {
  color: #4b5563;
}

.dropdown-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  z-index: 1000;
}

.dropdown-item {
  display: flex;
  flex-direction: column;
  padding: 0.7rem 1rem;
  cursor: pointer;
  border-bottom: 1px solid #f3f4f6;
  transition: background-color 0.15s;
}

.dropdown-item:last-child {
  border-bottom: none;
}

.dropdown-item:hover,
.dropdown-item.highlighted {
  background-color: #f0fdf4;
}

.athlete-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.athlete-name {
  font-weight: 600;
  color: #42b983;
  font-size: 0.95rem;
}

.athlete-sub {
  font-size: 0.8rem;
  color: #6b7280;
  margin-top: 0.2rem;
}

.badge {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.15rem 0.45rem;
  border-radius: 6px;
}

.badge-rating {
  background-color: #dcfce7;
  color: #166534;
}

.loading-item,
.empty-item {
  cursor: default;
  color: #6b7280;
  font-style: italic;
  text-align: center;
  padding: 1rem;
}

.dropdown-item.empty-item:hover,
.dropdown-item.loading-item:hover {
  background-color: transparent;
}
</style>
