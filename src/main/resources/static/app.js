/* ===== 民大朋友圈 · AITECC Moments ===== */
'use strict';

const API = '/api';
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

const state = {
  token: localStorage.getItem('mm_token') || '',
  me: null,
  posts: [],
  page: 0,
  hasNext: false,
  topic: '',
  draft: { images: [], lat: null, lng: null, locName: '' },
  openPostId: null,
};

/* ---------------- 基础工具 ---------------- */

function toast(msg) {
  const el = $('#toast');
  el.textContent = msg;
  el.classList.remove('hidden');
  clearTimeout(el._t);
  el._t = setTimeout(() => el.classList.add('hidden'), 1800);
}

async function api(path, opts = {}) {
  const headers = Object.assign({}, opts.headers || {});
  if (state.token) headers['X-Auth-Token'] = state.token;
  if (opts.json) {
    headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(opts.json);
  }
  const res = await fetch(API + path, Object.assign({}, opts, { headers }));
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (e) {
    data = null;
  }
  if (!res.ok) {
    const msg = (data && data.error) || ('请求失败（' + res.status + '）');
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return data;
}

/** 一律先转义再拼 HTML —— 用户昵称、正文、评论都不可信 */
function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[c]);
}

function relTime(iso) {
  const t = new Date(iso.replace(' ', 'T')).getTime();
  const diff = (Date.now() - t) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前';
  if (diff < 86400 * 7) return Math.floor(diff / 86400) + ' 天前';
  const d = new Date(t);
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}

function needLogin() {
  if (state.token) return true;
  toast('先登录再操作哦');
  openAuth('login');
  return false;
}

/* ---------------- 登录态 ---------------- */

async function loadMe() {
  if (!state.token) {
    state.me = null;
    renderMe();
    return;
  }
  try {
    state.me = await api('/auth/me');
  } catch (e) {
    state.token = '';
    localStorage.removeItem('mm_token');
    state.me = null;
  }
  renderMe();
}

function renderMe() {
  const box = $('#me');
  if (state.me) {
    box.innerHTML = `
      <div class="avatar">${esc(state.me.avatar || '🙂')}</div>
      <button class="btn-ghost" id="logoutBtn">退出</button>`;
    $('#logoutBtn').onclick = async () => {
      try {
        await api('/auth/logout', { method: 'POST' });
      } catch (e) {
        /* 退出失败也照常清本地 */
      }
      state.token = '';
      state.me = null;
      localStorage.removeItem('mm_token');
      renderMe();
      toast('已退出');
    };
  } else {
    box.innerHTML = `<button class="btn-ghost" id="loginBtn">登录 / 注册</button>`;
    $('#loginBtn').onclick = () => openAuth('login');
  }
}

/* ---------------- 信息流 ---------------- */

async function loadPosts(reset) {
  if (reset) {
    state.page = 0;
    state.posts = [];
  }
  const qs = new URLSearchParams({ page: state.page, size: 20 });
  if (state.topic) qs.set('topic', state.topic);
  const data = await api('/posts?' + qs);
  state.posts = state.posts.concat(data.items);
  state.hasNext = data.hasNext;
  renderFeed();
  $('#loadMore').classList.toggle('hidden', !state.hasNext);
}

function gridClass(n) {
  return 'grid g' + Math.min(n, 9);
}

function imageGrid(post) {
  const imgs = post.images || [];
  if (!imgs.length) return '';
  return `<div class="${gridClass(imgs.length)}">
    ${imgs
      .map(
        (im) =>
          `<div class="grid-item"><img src="${esc(im.url)}" alt="${esc(im.originName || '')}" loading="lazy" data-zoom="${esc(im.url)}"></div>`
      )
      .join('')}
  </div>`;
}

function metaRow(post) {
  const bits = [];
  if (post.topic) bits.push(`<span class="tag"># ${esc(post.topic)}</span>`);
  if (post.location && post.location.name) {
    bits.push(
      `<span class="loc" data-map="${esc(post.location.name)}" data-lat="${post.location.latitude || ''}" data-lng="${post.location.longitude || ''}">📍 ${esc(post.location.name)}</span>`
    );
  }
  return bits.length ? `<div class="meta">${bits.join('')}</div>` : '';
}

function postCard(p) {
  return `<article class="post" data-id="${p.id}">
    <div class="post-head">
      <div class="avatar">${esc(p.author.avatar || '🙂')}</div>
      <div class="who">
        <strong>${esc(p.author.nickname)}</strong>
        <div class="when">${relTime(p.createdAt)}</div>
      </div>
      ${p.mine ? '<button class="del" data-del="' + p.id + '">删除</button>' : ''}
    </div>
    ${p.content ? `<p class="content">${esc(p.content)}</p>` : ''}
    ${imageGrid(p)}
    ${metaRow(p)}
    <div class="actions">
      <button class="act ${p.liked ? 'liked' : ''}" data-like="${p.id}">
        <span class="heart">${p.liked ? '❤️' : '🤍'}</span><span class="cnt">${p.likeCount || ''}</span>
      </button>
      <button class="act" data-open="${p.id}">💬 <span>${p.commentCount || ''}</span></button>
    </div>
  </article>`;
}

function renderFeed() {
  const box = $('#feed');
  if (!state.posts.length) {
    box.innerHTML = `<div class="empty">还没有动态，点右下角 ➕ 发第一条吧</div>`;
    return;
  }
  box.innerHTML = state.posts.map(postCard).join('');
}

$('#feed').addEventListener('click', async (e) => {
  const zoom = e.target.closest('[data-zoom]');
  if (zoom) {
    $('#lightboxImg').src = zoom.dataset.zoom;
    $('#lightbox').classList.remove('hidden');
    return;
  }
  const del = e.target.closest('[data-del]');
  if (del) {
    if (!confirm('删掉这条动态？')) return;
    await api('/posts/' + del.dataset.del, { method: 'DELETE' });
    state.posts = state.posts.filter((p) => String(p.id) !== del.dataset.del);
    renderFeed();
    toast('已删除');
    return;
  }
  const like = e.target.closest('[data-like]');
  if (like) {
    if (!needLogin()) return;
    const id = like.dataset.like;
    const liked = like.classList.contains('liked');
    const r = await api('/posts/' + id + '/like', { method: liked ? 'DELETE' : 'POST' });
    like.classList.toggle('liked', r.liked);
    like.querySelector('.heart').textContent = r.liked ? '❤️' : '🤍';
    like.querySelector('.cnt').textContent = r.count || '';
    const p = state.posts.find((x) => String(x.id) === id);
    if (p) {
      p.liked = r.liked;
      p.likeCount = r.count;
    }
    return;
  }
  const open = e.target.closest('[data-open]');
  if (open) openDetail(open.dataset.open);
});

$('#lightbox').addEventListener('click', () => $('#lightbox').classList.add('hidden'));

$('#loadMoreBtn').onclick = () => {
  state.page += 1;
  loadPosts(false);
};

/* ---------------- 详情与评论 ---------------- */

async function openDetail(id) {
  if (!state.token) state.openPostId = id;
  const [post, comments] = await Promise.all([
    api('/posts/' + id),
    api('/posts/' + id + '/comments').catch(() => []),
  ]);
  state.openPostId = id;
  $('#detailBody').innerHTML = `
    ${postCard(post)}
    <div class="comments">${comments
      .map(
        (c) => `<div class="comment" data-cid="${c.id}">
        <div class="avatar">${esc(c.author.avatar || '🙂')}</div>
        <div class="c-main">
          <div class="c-top">
            <span class="c-name">${esc(c.author.nickname)}</span>
            <span class="c-time">${relTime(c.createdAt)}</span>
            ${c.mine ? '<button class="c-del" data-cdel="' + c.id + '">删除</button>' : ''}
          </div>
          <p class="c-text">${esc(c.content)}</p>
        </div>
      </div>`
      )
      .join('')}</div>
    ${comments.length ? '' : '<p class="empty">还没有评论，来说两句</p>'}`;
  $('#detail').classList.remove('hidden');
}

$('#detailBody').addEventListener('click', async (e) => {
  const cd = e.target.closest('[data-cdel]');
  if (cd) {
    await api('/comments/' + cd.dataset.cdel, { method: 'DELETE' });
    openDetail(state.openPostId);
    toast('评论已删除');
  }
});

$('#commentSend').onclick = async () => {
  if (!needLogin()) return;
  const input = $('#commentInput');
  const content = input.value.trim();
  if (!content) return;
  await api('/posts/' + state.openPostId + '/comments', { method: 'POST', json: { content } });
  input.value = '';
  await openDetail(state.openPostId);
  loadPosts(true);
};

/* ---------------- 发布 ---------------- */

$('#fab').onclick = () => {
  if (!needLogin()) return;
  $('#composer').classList.remove('hidden');
};

$('#pickImg').onclick = () => $('#fileInput').click();

$('#fileInput').onchange = (e) => {
  const files = Array.from(e.target.files || []);
  files.forEach((f) => {
    if (state.draft.images.length >= 9) return;
    state.draft.images.push({ file: f, preview: URL.createObjectURL(f) });
  });
  e.target.value = '';
  renderDraftImages();
};

function renderDraftImages() {
  $('#imgPreview').innerHTML = state.draft.images
    .map(
      (im, i) =>
        `<div class="grid-item"><img src="${im.preview}"><button class="rm" data-rmi="${i}">✕</button></div>`
    )
    .join('');
  $('#imgPreview').className = state.draft.images.length
    ? gridClass(state.draft.images.length)
    : 'grid hidden';
}

$('#imgPreview').addEventListener('click', (e) => {
  const rm = e.target.closest('[data-rmi]');
  if (rm) {
    const i = Number(rm.dataset.rmi);
    URL.revokeObjectURL(state.draft.images[i].preview);
    state.draft.images.splice(i, 1);
    renderDraftImages();
  }
});

$('#pickLoc').onclick = () => {
  // 浏览器定位只在 https 或 localhost 下可用；拿不到就让手填地名
  if (!navigator.geolocation) {
    const name = prompt('输入地点名称（例如：西校区食堂三楼）');
    if (name) setLocation(name, null, null);
    return;
  }
  toast('正在定位…');
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      const name = prompt('定位成功，给它起个名字：', state.draft.locName || '我在这里');
      setLocation(name || '我在这里', pos.coords.latitude, pos.coords.longitude);
    },
    () => {
      const name = prompt('自动定位失败，手动填个地点名吧（例如：图书馆北门）');
      if (name) setLocation(name, null, null);
    },
    { timeout: 8000, maximumAge: 60000 }
  );
};

function setLocation(name, lat, lng) {
  state.draft.locName = name;
  state.draft.lat = lat;
  state.draft.lng = lng;
  $('#locHint').textContent = '📍 ' + name + (lat ? ` (${lat.toFixed(4)}, ${lng.toFixed(4)})` : '');
}

$('#postSubmit').onclick = async () => {
  const content = $('#postContent').value.trim();
  if (!content && !state.draft.images.length) {
    $('#postError').textContent = '写点什么，或者至少发张图';
    return;
  }
  const fd = new FormData();
  fd.append('content', content);
  fd.append('topic', $('#topicSel').value || '');
  fd.append('locationName', state.draft.locName || '');
  if (state.draft.lat != null) fd.append('latitude', String(state.draft.lat));
  if (state.draft.lng != null) fd.append('longitude', String(state.draft.lng));
  state.draft.images.forEach((im) => fd.append('images', im.file));

  const btn = $('#postSubmit');
  btn.disabled = true;
  try {
    await api('/posts', { method: 'POST', body: fd });
    $('#postContent').value = '';
    state.draft = { images: [], lat: null, lng: null, locName: '' };
    renderDraftImages();
    $('#locHint').textContent = '';
    $('#postError').textContent = '';
    $('#composer').classList.add('hidden');
    await loadPosts(true);
    toast('发布成功 🎉');
  } catch (e) {
    $('#postError').textContent = e.message;
  } finally {
    btn.disabled = false;
  }
};

/* ---------------- 登录注册 ---------------- */

const EMOJIS = ['🙂', '😎', '🐱', '🐶', '🍜', '🎮', '📚', '🌵', '🚀', '🎧', '🍀', '🔥'];

function openAuth(tab) {
  if (!$('#emojiPick').childElementCount) {
    $('#emojiPick').innerHTML = EMOJIS.map((e) => `<button data-emoji="${e}">${e}</button>`).join('');
  }
  switchTab(tab || 'login');
  $('#authModal').classList.remove('hidden');
}

function switchTab(tab) {
  $$('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === tab));
  $('#avatarRow').classList.toggle('hidden', tab !== 'register');
  $('#authTitle').textContent = tab === 'login' ? '登录' : '注册';
  $('#authSubmit').textContent = tab === 'login' ? '登录' : '注册并进入';
  $('#authModal').dataset.mode = tab;
}

$$('.tab').forEach((t) => (t.onclick = () => switchTab(t.dataset.tab)));

$('#emojiPick').addEventListener('click', (e) => {
  const b = e.target.closest('[data-emoji]');
  if (b) $('#authAvatar').value = b.dataset.emoji;
});

$('#authSubmit').onclick = async () => {
  const mode = $('#authModal').dataset.mode || 'login';
  const body = {
    nickname: $('#authNickname').value.trim(),
    password: $('#authPassword').value,
  };
  if (mode === 'register') body.avatar = $('#authAvatar').value.trim() || '🙂';
  $('#authError').textContent = '';
  try {
    const r = await api('/auth/' + mode, { method: 'POST', json: body });
    state.token = r.token;
    state.me = r.user;
    localStorage.setItem('mm_token', r.token);
    $('#authModal').classList.add('hidden');
    $('#authPassword').value = '';
    renderMe();
    await loadPosts(true);
    toast(mode === 'login' ? '欢迎回来 👋' : '注册成功，欢迎加入 🎉');
  } catch (e) {
    $('#authError').textContent = e.message;
  }
};

/* ---------------- 通用交互 ---------------- */

$$('[data-close]').forEach(
  (b) => (b.onclick = () => $('#' + b.dataset.close).classList.add('hidden'))
);
$$('.modal').forEach((m) =>
  m.addEventListener('click', (e) => {
    if (e.target === m) m.classList.add('hidden');
  })
);

/** 点定位信息跳转高德地图；只有地名没有坐标时就提示一下 */
document.addEventListener('click', (e) => {
  const loc = e.target.closest('[data-map]');
  if (!loc) return;
  const { lat, lng, map: name } = loc.dataset;
  if (lat && lng) {
    const url = `https://uri.amap.com/marker?position=${lng},${lat}&name=${encodeURIComponent(name)}`;
    window.open(url, '_blank', 'noopener');
  } else {
    toast('📍 ' + name);
  }
});

async function loadTopics() {
  try {
    const topics = await api('/posts/topics');
    const all = ['推荐'].concat(topics);
    $('#topics').innerHTML = all
      .map(
        (t) =>
          `<button class="topic ${t === '推荐' && !state.topic ? 'active' : ''}" data-topic="${esc(t)}">${esc(t)}</button>`
      )
      .join('');
    $('#topicSel').innerHTML =
      `<option value="">选择话题</option>` +
      topics.map((t) => `<option value="${esc(t)}">${esc(t)}</option>`).join('');
  } catch (e) {
    /* 话题栏挂了不影响刷动态 */
  }
}

$('#topics').addEventListener('click', (e) => {
  const t = e.target.closest('[data-topic]');
  if (!t) return;
  const topic = t.dataset.topic === '推荐' ? '' : t.dataset.topic;
  state.topic = topic;
  $$('.topic').forEach((x) => x.classList.toggle('active', x === t));
  loadPosts(true);
});

/* ---------------- 启动 ---------------- */

(async function boot() {
  await loadMe();
  await loadTopics();
  try {
    await loadPosts(true);
  } catch (e) {
    $('#feed').innerHTML = `<div class="empty">加载失败：${esc(e.message)}</div>`;
  }
})();
