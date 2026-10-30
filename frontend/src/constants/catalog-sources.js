export const DEFAULT_SOURCE = 'bf';
export const CATALOG_SOURCES = [
  { value: 'bf', label: '暴风资源' },
  { value: 'zy360', label: '360资源' },
];

export const normalizeSource = (source) => CATALOG_SOURCES.some((item) => item.value === source) ? source : DEFAULT_SOURCE;
