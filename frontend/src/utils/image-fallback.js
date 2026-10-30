export const handleImageError = (event) => {
  const image = event.currentTarget;
  if (!(image instanceof HTMLImageElement) || image.dataset.fallback) return;
  image.dataset.fallback = 'true';
  image.src = '/whale/dafeiyu-chibi.png';
  image.classList.add('poster-fallback');
};
