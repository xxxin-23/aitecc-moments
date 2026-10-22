/* ===== 科技社陪着你走过青葱校园 ===== */
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
  tab: 'recommend', // recommend | latest | favorites
  draft: { images: [], lat: null, lng: null, locName: '' },
  openPostId: null,
  editingId: null, // 非空表示正在编辑某条动态
};

/** 三类「开关型」互动：图标、字段名、按钮文案都收在一处，加新动作只改这里 */
const ACTS = {
  like: { on: 'liked', count: 'likeCount', icons: ['🤍', '❤️'], label: '赞', toast: ['已点赞 ❤️', '取消点赞'] },
  favorite: { on: 'favorited', count: 'favoriteCount', icons: ['☆', '⭐'], label: '收藏', toast: ['已收藏 ⭐', '取消收藏'] },
  recommend: { on: 'recommended', count: 'recommendCount', icons: ['👍', '🌟'], label: '推荐', toast: ['已推荐给同学 🌟', '取消推荐'] },
};

const EMOJIS = ['🐱', '🎀', '🌸', '🍓', '🧁', '🍰', '💗', '🦄', '🌷', '🐰', '🍡', '🙂'];

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
  let res;
  try {
    res = await fetch(API + path, Object.assign({}, opts, { headers }));
  } catch (e) {
    throw new Error('连不上服务器，检查一下网络或者服务有没有开');
  }
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
  const t = new Date(String(iso).replace(' ', 'T')).getTime();
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
  toast('先登录再操作哦 🎀');
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
      <button class="avatar-btn" id="meAvatar" title="我的资料">
        <div class="avatar">${esc(state.me.avatar || '🙂')}</div>
      </button>
      <button class="btn-ghost" id="logoutBtn">退出</button>`;
    $('#meAvatar').onclick = openProfile;
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
      if (state.tab === 'favorites') switchTab('recommend');
      else loadPosts(true);
      toast('已退出，下次见 👋');
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
  let path;
  if (state.tab === 'favorites') {
    path = '/me/favorites?' + qs;
  } else {
    if (state.topic) qs.set('topic', state.topic);
    qs.set('sort', state.tab === 'latest' ? 'latest' : 'recommend');
    path = '/posts?' + qs;
  }
  try {
    const data = await api(path);
    state.posts = state.posts.concat(data.items);
    state.hasNext = data.hasNext;
    renderFeed();
    $('#loadMore').classList.toggle('hidden', !state.hasNext);
  } catch (e) {
    if (state.tab === 'favorites' && e.status === 401) {
      switchTab('recommend');
      return;
    }
    $('#feed').innerHTML = `<div class="empty"><span class="big">🎀</span>加载失败：${esc(e.message)}</div>`;
  }
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

function actButton(kind, p) {
  const cfg = ACTS[kind];
  const on = !!p[cfg.on];
  const count = p[cfg.count] || 0;
  return `<button class="act ${on ? 'on' : ''}" data-act="${kind}" data-id="${p.id}" title="${cfg.label}">
    <span class="ico">${on ? cfg.icons[1] : cfg.icons[0]}</span><span class="cnt">${count || ''}</span>${cfg.label}
  </button>`;
}

function postCard(p) {
  return `<article class="post" data-id="${p.id}">
    <div class="post-head">
      <div class="avatar">${esc(p.author.avatar || '🙂')}</div>
      <div class="who">
        <strong>${esc(p.author.nickname)}</strong>
        <div class="when">${relTime(p.createdAt)}</div>
      </div>
      ${
        p.mine
          ? `<div class="head-actions">
        <button data-edit="${p.id}">编辑</button>
        <button class="del" data-del="${p.id}">删除</button>
      </div>`
          : ''
      }
    </div>
    ${p.content ? `<p class="content">${esc(p.content)}</p>` : ''}
    ${imageGrid(p)}
    ${metaRow(p)}
    <div class="actions">
      ${actButton('like', p)}
      ${actButton('favorite', p)}
      ${actButton('recommend', p)}
      <button class="act" data-open="${p.id}" title="评论">💬<span class="cnt">${p.commentCount || ''}</span>评论</button>
    </div>
  </article>`;
}

function renderFeed() {
  const box = $('#feed');
  if (!state.posts.length) {
    const empty = {
      favorites: ['⭐', '还没有收藏，看到喜欢的点个收藏吧'],
      latest: ['🌸', '还没有动态，点右下角 ➕ 发第一条吧'],
      recommend: ['🎀', '还没有动态，点右下角 ➕ 发第一条吧'],
    }[state.tab];
    box.innerHTML = `<div class="empty"><span class="big">${empty[0]}</span>${empty[1]}</div>`;
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
  const edit = e.target.closest('[data-edit]');
  if (edit) {
    openEditor(edit.dataset.edit);
    return;
  }
  const act = e.target.closest('[data-act]');
  if (act) {
    toggleAct(act.dataset.act, act.dataset.id, act);
    return;
  }
  const open = e.target.closest('[data-open]');
  if (open) openDetail(open.dataset.open);
});

/** 点赞 / 收藏 / 推荐：点了就切换，服务端幂等，返回最新状态和数量 */
async function toggleAct(kind, id, el) {
  if (!needLogin()) return;
  const cfg = ACTS[kind];
  const on = el.classList.contains('on');
  try {
    const r = await api('/posts/' + id + '/' + kind, { method: on ? 'DELETE' : 'POST' });
    el.classList.toggle('on', r.on);
    el.querySelector('.ico').textContent = r.on ? cfg.icons[1] : cfg.icons[0];
    el.querySelector('.cnt').textContent = r.count || '';
    toast(r.on ? cfg.toast[0] : cfg.toast[1]);
    const p = state.posts.find((x) => String(x.id) === id);
    if (p) {
      p[cfg.on] = r.on;
      p[cfg.count] = r.count;
      // 在「我的收藏」里取消收藏，就直接从列表里划掉
      if (kind === 'favorite' && !r.on && state.tab === 'favorites') {
        state.posts = state.posts.filter((x) => String(x.id) !== id);
        renderFeed();
      }
    }
  } catch (e) {
    toast(e.message);
  }
}

$('#lightbox').addEventListener('click', () => $('#lightbox').classList.add('hidden'));

$('#loadMoreBtn').onclick = () => {
  state.page += 1;
  loadPosts(false);
};

/* ---------------- 详情与评论 ---------------- */

async function openDetail(id) {
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
    ${comments.length ? '' : '<p class="empty"><span class="big">💬</span>还没有评论，来说两句</p>'}`;
  $('#detail').classList.remove('hidden');
}

$('#detailBody').addEventListener('click', async (e) => {
  const cd = e.target.closest('[data-cdel]');
  if (cd) {
    await api('/comments/' + cd.dataset.cdel, { method: 'DELETE' });
    openDetail(state.openPostId);
    loadPosts(true);
    toast('评论已删除');
    return;
  }
  const act = e.target.closest('[data-act]');
  if (act) {
    await toggleAct(act.dataset.act, act.dataset.id, act);
    openDetail(state.openPostId);
    return;
  }
  const edit = e.target.closest('[data-edit]');
  if (edit) {
    $('#detail').classList.add('hidden');
    openEditor(edit.dataset.edit);
    return;
  }
  const open = e.target.closest('[data-open]');
  if (open) openDetail(open.dataset.open);
});

$('#commentSend').onclick = async () => {
  if (!needLogin()) return;
  const input = $('#commentInput');
  const content = input.value.trim();
  if (!content) return;
  try {
    await api('/posts/' + state.openPostId + '/comments', { method: 'POST', json: { content } });
    input.value = '';
    await openDetail(state.openPostId);
    loadPosts(true);
    toast('评论成功 💬');
  } catch (e) {
    toast(e.message);
  }
};

/* ---------------- 发布 ---------------- */

/** 打开「发一条」面板（新建模式） */
function openComposer() {
  state.editingId = null;
  $('#postContent').value = '';
  $('#topicSel').value = '';
  state.draft = { images: [], lat: null, lng: null, locName: '' };
  renderDraftImages();
  $('#locHint').textContent = '';
  $('#postError').textContent = '';
  $('#composerTitle').textContent = '发一条';
  $('#postSubmit').textContent = '发布';
  $('#pickImg').classList.remove('hidden');
  $('#composer').classList.remove('hidden');
}

/** 打开「编辑」面板：只改文字 / 话题 / 定位，图片不动 */
async function openEditor(id) {
  if (!needLogin()) return;
  const post = state.posts.find((p) => String(p.id) === String(id)) || (await api('/posts/' + id));
  state.editingId = post.id;
  $('#postContent').value = post.content || '';
  $('#topicSel').value = post.topic || '';
  state.draft = { images: [], lat: null, lng: null, locName: '' };
  renderDraftImages();
  const loc = post.location;
  if (loc && (loc.name || loc.latitude != null)) {
    setLocation(loc.name || '', loc.latitude, loc.longitude);
  } else {
    $('#locHint').textContent = '';
  }
  $('#postError').textContent = '';
  $('#composerTitle').textContent = '编辑动态';
  $('#postSubmit').textContent = '保存修改';
  $('#pickImg').classList.add('hidden'); // 编辑不改图，避免误导
  $('#composer').classList.remove('hidden');
}

$('#fab').onclick = () => {
  if (!needLogin()) return;
  openComposer();
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
  if (!content && !state.draft.images.length && !state.editingId) {
    $('#postError').textContent = '写点什么，或者至少发张图';
    return;
  }
  const topic = $('#topicSel').value || '';
  const btn = $('#postSubmit');
  btn.disabled = true;
  try {
    if (state.editingId) {
      // 编辑：只提交文字 / 话题 / 定位
      await api('/posts/' + state.editingId, {
        method: 'PATCH',
        json: {
          content,
          topic,
          locationName: state.draft.locName || '',
          latitude: state.draft.lat,
          longitude: state.draft.lng,
        },
      });
      toast('已保存 ✏️');
    } else {
      const fd = new FormData();
      fd.append('content', content);
      fd.append('topic', topic);
      fd.append('locationName', state.draft.locName || '');
      if (state.draft.lat != null) fd.append('latitude', String(state.draft.lat));
      if (state.draft.lng != null) fd.append('longitude', String(state.draft.lng));
      state.draft.images.forEach((im) => fd.append('images', im.file));
      await api('/posts', { method: 'POST', body: fd });
      toast('发布成功 🎉');
    }
    $('#postContent').value = '';
    state.draft = { images: [], lat: null, lng: null, locName: '' };
    state.editingId = null;
    renderDraftImages();
    $('#locHint').textContent = '';
    $('#postError').textContent = '';
    $('#composer').classList.add('hidden');
    await loadPosts(true);
  } catch (e) {
    $('#postError').textContent = e.message;
  } finally {
    btn.disabled = false;
  }
};

/* ---------------- 登录注册 ---------------- */

function openAuth(tab) {
  if (!$('#emojiPick').childElementCount) {
    $('#emojiPick').innerHTML = EMOJIS.map((e) => `<button data-emoji="${e}">${e}</button>`).join('');
  }
  switchAuthTab(tab || 'login');
  $('#authModal').classList.remove('hidden');
}

function switchAuthTab(tab) {
  $$('#authModal .tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === tab));
  $('#avatarRow').classList.toggle('hidden', tab !== 'register');
  $('#authTitle').textContent = tab === 'login' ? '登录' : '注册';
  $('#authSubmit').textContent = tab === 'login' ? '登录' : '注册并进入';
  $('#authModal').dataset.mode = tab;
}

$$('#authModal .tab').forEach((t) => (t.onclick = () => switchAuthTab(t.dataset.tab)));

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
  if (mode === 'register') body.avatar = $('#authAvatar').value.trim() || '🐱';
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

/* ---------------- 个人资料（改昵称 / 头像） ---------------- */

function openProfile() {
  if (!needLogin()) return;
  if (!$('#profileEmojiPick').childElementCount) {
    $('#profileEmojiPick').innerHTML = EMOJIS.map(
      (e) => `<button data-pemoji="${e}">${e}</button>`
    ).join('');
  }
  const me = state.me || {};
  $('#profileAvatar').textContent = me.avatar || '🙂';
  $('#profileNickname').textContent = me.nickname || '—';
  $('#profileName').value = me.nickname || '';
  $('#profileAvatarInput').value = me.avatar || '';
  $('#profileBio').value = me.bio || '';
  $('#profileError').textContent = '';
  $('#profileStats').textContent = '加载中…';
  api('/me/stats')
    .then((s) => {
      $('#profileStats').textContent = `⭐ 收藏 ${s.favoriteCount} · 🌟 推荐 ${s.recommendCount}`;
    })
    .catch(() => {
      $('#profileStats').textContent = '';
    });
  $('#profileModal').classList.remove('hidden');
}

$('#profileEmojiPick').addEventListener('click', (e) => {
  const b = e.target.closest('[data-pemoji]');
  if (b) {
    $('#profileAvatarInput').value = b.dataset.pemoji;
    $('#profileAvatar').textContent = b.dataset.pemoji;
  }
});

$('#profileSubmit').onclick = async () => {
  const btn = $('#profileSubmit');
  btn.disabled = true;
  $('#profileError').textContent = '';
  try {
    const updated = await api('/auth/profile', {
      method: 'PATCH',
      json: {
        nickname: $('#profileName').value.trim(),
        avatar: $('#profileAvatarInput').value.trim(),
        bio: $('#profileBio').value.trim(),
      },
    });
    state.me = Object.assign({}, state.me, updated);
    $('#profileModal').classList.add('hidden');
    renderMe();
    await loadPosts(true); // 昵称改了，列表里的署名要跟着变
    toast('资料已更新 🎀');
  } catch (e) {
    $('#profileError').textContent = e.message;
  } finally {
    btn.disabled = false;
  }
};

/* ---------------- 通用交互 ---------------- */

/** 退出编辑态：编辑面板关掉后，下次点 ➕ 应该回到「发一条」 */
function exitEditing() {
  state.editingId = null;
  $('#pickImg').classList.remove('hidden');
}

$$('[data-close]').forEach((b) => {
  b.onclick = () => {
    $('#' + b.dataset.close).classList.add('hidden');
    if (b.dataset.close === 'composer') exitEditing();
  };
});
$$('.modal').forEach((m) =>
  m.addEventListener('click', (e) => {
    if (e.target !== m) return;
    m.classList.add('hidden');
    if (m.id === 'composer') exitEditing();
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
    $('#topics').innerHTML = ['全部']
      .concat(topics)
      .map(
        (t) =>
          `<button class="topic ${(t === '全部' && !state.topic) || t === state.topic ? 'active' : ''}" data-topic="${esc(t)}">${esc(t)}</button>`
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
  state.topic = t.dataset.topic === '全部' ? '' : t.dataset.topic;
  $$('.topic').forEach((x) => x.classList.toggle('active', x === t));
  loadPosts(true);
});

function switchTab(tab) {
  state.tab = tab;
  $$('.feed-tab').forEach((x) => x.classList.toggle('active', x.dataset.tab === tab));
  loadPosts(true);
}

$$('.feed-tab').forEach((b) => {
  b.onclick = () => {
    if (b.dataset.tab === 'favorites' && !state.token) {
      toast('登录后才能看收藏哦 🎀');
      openAuth('login');
      return;
    }
    switchTab(b.dataset.tab);
  };
});

/* ---------------- 启动 ---------------- */

(async function boot() {
  await loadMe();
  await loadTopics();
  await loadPosts(true);
})();
