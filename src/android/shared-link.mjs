const ID = /^[1-9][0-9]{0,29}$/;
const HOSTS = new Set(['hongguoduanju.com', 'www.hongguoduanju.com']);
const URL_PATTERN = /https:\/\/[^\s<>"'，。！？；、【】（）]+/gi;

function uniqueId(values) {
  const ids = [...new Set(values)];
  if (ids.length > 1 || ids.some(value => !ID.test(value))) throw new Error('分享链接的剧集编号无效或相互冲突');
  return ids[0] ?? null;
}

function parseOfficialUrl(text) {
  let url;
  try { url = new URL(text); } catch { return null; }
  if (!HOSTS.has(url.hostname) || url.protocol !== 'https:') return null;
  if (url.username || url.password || url.port || text.includes('\\')) throw new Error('红果链接格式不支持');
  const path = url.pathname.match(/^\/player\/([0-9]+)(?:\/([0-9]+))?\/?$/);
  if (!path && /^\/player\//.test(url.pathname)) throw new Error('分享链接的播放地址无效');
  const seriesId = uniqueId([...(path ? [path[1]] : []), ...url.searchParams.getAll('series_id'), ...url.searchParams.getAll('video_series_id')]);
  const vid = uniqueId([...(path?.[2] ? [path[2]] : []), ...url.searchParams.getAll('vid')]);
  if (!seriesId) throw new Error('链接中没有红果剧集编号');
  return { seriesId, vid };
}

export function parseSharedLink(value, { allowId = false } = {}) {
  if (typeof value !== 'string' || !value.trim() || value.length > 4096) throw new Error('请粘贴有效的红果分享链接');
  const text = value.trim();
  if (allowId && ID.test(text)) return { seriesId: text, vid: null };
  const links = (text.match(URL_PATTERN) || []).map(url => parseOfficialUrl(url.replace(/[),.;!]+$/, ''))).filter(Boolean);
  if (!links.length) throw new Error('请使用包含红果剧集编号的官网链接');
  const [target] = links;
  if (links.some(link => link.seriesId !== target.seriesId || link.vid !== target.vid)) throw new Error('内容包含多个不同的红果链接，请只粘贴一个');
  return target;
}
