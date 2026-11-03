// sw.js —— 断网时的 Kitty 提示页（离线兜底）
// 只拦截「导航请求」(mode === 'navigate')，不影响 API / 静态资源，避免断网时接口被错误兜底成 HTML
const CACHE = 'kitty-offline-v1';
const OFFLINE = '/offline-kitty.html';

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE).then((c) => c.add(OFFLINE)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  if (e.request.mode === 'navigate') {
    e.respondWith(fetch(e.request).catch(() => caches.match(OFFLINE)));
  }
});
