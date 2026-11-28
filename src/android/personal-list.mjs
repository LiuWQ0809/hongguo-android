export const LIBRARY_GENRES = ['真人剧', '漫剧', 'AI剧', '未分类'];
const GENRE_LABELS = new Map([
  ['真人剧', '真人剧'], ['真人短剧', '真人剧'],
  ['漫剧', '漫剧'], ['动态漫', '漫剧'], ['漫画短剧', '漫剧'],
  ['ai剧', 'AI剧'], ['ai短剧', 'AI剧'],
]);

const normalize = value => String(value ?? '').normalize('NFKC').trim().toLowerCase();

export function libraryGenre(row) {
  const tags = Array.isArray(row?.tags) ? row.tags : [];
  const labels = new Set(tags.map(tag => GENRE_LABELS.get(normalize(tag))).filter(Boolean));
  return labels.size === 1 ? [...labels][0] : '未分类';
}

export function matchesPersonalTitle(title, query) {
  return normalize(title).includes(normalize(query));
}
