import { ref, onUnmounted } from 'vue';

export function useLoadingTimer() {
  const isLoading = ref(false);
  const elapsedSeconds = ref(0);
  let timer = null;

  const start = () => {
    isLoading.value = true;
    elapsedSeconds.value = 0;
    clearInterval(timer);
    timer = setInterval(() => {
      elapsedSeconds.value++;
    }, 1000);
  };

  const stop = () => {
    isLoading.value = false;
    clearInterval(timer);
    timer = null;
  };

  onUnmounted(() => {
    clearInterval(timer);
  });

  return {
    isLoading,
    elapsedSeconds,
    start,
    stop
  };
}
