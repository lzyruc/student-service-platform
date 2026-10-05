import { computed, ref, watch } from "vue";
import type { Ref, ComputedRef } from "vue";

/** Pagination for APIs that already return the complete list. Filters reset the page. */
export function useTablePagination<T>(rows: Ref<T[]> | ComputedRef<T[]>) {
  const page = ref(1);
  const pageSize = ref(15);
  watch([rows, pageSize], () => {
    page.value = 1;
  });
  const pagedRows = computed(() => rows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
  return { page, pageSize, pagedRows };
}
