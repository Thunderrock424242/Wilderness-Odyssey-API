/* ═══════════════════════════════════════════════════════
   Wilderness Odyssey — main.js
   Handles: cursor, nav, cinematic bars, hero canvas
            particles, scroll reveal, parallax, gallery
══════════════════════════════════════════════════════════ */

/* ── CURSOR ─────────────────────────────────────────── */
const cur  = document.getElementById('cur');
const curt = document.getElementById('curt');
let cx = 0, cy = 0;

document.addEventListener('mousemove', e => {
  cx = e.clientX; cy = e.clientY;
  cur.style.left = cx + 'px';
  cur.style.top  = cy + 'px';
});

setInterval(() => {
  curt.style.left = cx + 'px';
  curt.style.top  = cy + 'px';
}, 90);

document.querySelectorAll('a, button').forEach(el => {
  el.addEventListener('mouseenter', () => cur.style.transform = 'translate(-50%,-50%) scale(2.2)');
  el.addEventListener('mouseleave', () => cur.style.transform = 'translate(-50%,-50%) scale(1)');
});

/* ── SCROLL PROGRESS BAR ────────────────────────────── */
const prog = document.getElementById('prog');
window.addEventListener('scroll', () => {
  const pct = window.scrollY / (document.body.scrollHeight - window.innerHeight) * 100;
  prog.style.width = pct + '%';
}, { passive: true });

/* ── NAV SOLID ON SCROLL ────────────────────────────── */
const nav = document.getElementById('nav');
window.addEventListener('scroll', () => {
  nav.classList.toggle('solid', window.scrollY > 60);
}, { passive: true });

/* ── CINEMATIC BARS OPEN ON LOAD ────────────────────── */
window.addEventListener('load', () => {
  setTimeout(() => document.getElementById('hero').classList.add('bars-open'), 80);
});

/* ── HERO CANVAS PARTICLE SYSTEM ────────────────────── */
const canvas = document.getElementById('hero-canvas');
const ctx    = canvas.getContext('2d');
let W, H, particles = [], stars = [];

function resize() {
  W = canvas.width  = window.innerWidth;
  H = canvas.height = window.innerHeight;
}
resize();
window.addEventListener('resize', () => { resize(); initParticles(); }, { passive: true });

function initParticles() {
  particles = [];
  stars     = [];

  // Background stars
  for (let i = 0; i < 250; i++) {
    stars.push({
      x:   Math.random() * W,
      y:   Math.random() * H,
      r:   Math.random() * 1.35 + 0.2,
      a:   Math.random() * 0.52 + 0.07,
      spd: Math.random() * 0.014 + 0.003,
      dir: Math.random() > 0.5 ? 1 : -1,
    });
  }

  // Particles (blue wisps + orange embers)
  for (let i = 0; i < 72; i++) spawnParticle();
}

function spawnParticle() {
  const wisp = Math.random() > 0.4; // 60% wisps, 40% embers
  particles.push({
    x:       Math.random() * W,
    y:       H + Math.random() * 110,
    r:       wisp ? Math.random() * 2.3 + 0.7 : Math.random() * 1.55 + 0.35,
    vx:      (Math.random() - 0.5) * (wisp ? 0.72 : 0.24),
    vy:      -(Math.random() * (wisp ? 0.48 : 0.72) + 0.1),
    a:       0,
    maxA:    wisp ? Math.random() * 0.52 + 0.22 : Math.random() * 0.62 + 0.2,
    hue:     wisp ? 196 + Math.random() * 26 : 11 + Math.random() * 15,
    sat:     wisp ? 86 : 100,
    lum:     wisp ? 65 : 60,
    life:    0,
    maxLife: Math.random() * (wisp ? 570 : 265) + 200,
    glow:    wisp ? Math.random() * 21 + 9 : Math.random() * 13 + 4,
    flicker: !wisp,
    wisp,
  });
}

function drawParticles() {
  ctx.clearRect(0, 0, W, H);

  // Stars
  for (const s of stars) {
    s.a += s.spd * s.dir;
    if (s.a > 0.6 || s.a < 0.04) s.dir *= -1;
    ctx.beginPath();
    ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(215,222,240,${s.a})`;
    ctx.fill();
  }

  // Particles
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.life++;

    // Movement — wisps drift sideways, embers rise more directly
    p.x += p.vx + (p.wisp ? Math.sin(p.life * 0.022) * 0.54 : Math.sin(p.life * 0.042) * 0.11);
    p.y += p.vy + (p.flicker ? (Math.random() - 0.5) * 0.07 : 0);

    // Fade in/out over lifetime
    const pr = p.life / p.maxLife;
    let al = pr < 0.12 ? (pr / 0.12) * p.maxA
           : pr > 0.7  ? ((1 - pr) / 0.3) * p.maxA
           : p.maxA;
    if (p.flicker) al *= 0.74 + Math.random() * 0.26; // embers flicker

    // Glow halo
    const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.glow);
    g.addColorStop(0,   `hsla(${p.hue},${p.sat}%,${p.lum}%,${al * 0.88})`);
    g.addColorStop(0.4, `hsla(${p.hue},${p.sat}%,${p.lum}%,${al * 0.25})`);
    g.addColorStop(1,   `hsla(${p.hue},${p.sat}%,${p.lum}%,0)`);
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.glow, 0, Math.PI * 2);
    ctx.fillStyle = g;
    ctx.fill();

    // Bright core dot
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
    ctx.fillStyle = `hsla(${p.hue},${p.sat}%,83%,${al})`;
    ctx.fill();

    if (p.life >= p.maxLife) { particles.splice(i, 1); spawnParticle(); }
  }

  requestAnimationFrame(drawParticles);
}

initParticles();
drawParticles();

/* ── SCROLL REVEAL ──────────────────────────────────── */
const revealObs = new IntersectionObserver(entries => {
  entries.forEach(e => { if (e.isIntersecting) e.target.classList.add('in'); });
}, { threshold: 0.11, rootMargin: '0px 0px -50px 0px' });

document.querySelectorAll('.rx').forEach(el => revealObs.observe(el));

/* ── HERO PARALLAX ──────────────────────────────────── */
window.addEventListener('scroll', () => {
  const s  = window.scrollY;
  const hc = document.querySelector('.h-content');
  if (hc && s < window.innerHeight) {
    hc.style.transform = `translateY(${s * 0.26}px)`;
    hc.style.opacity   = Math.max(0, 1 - (s / (window.innerHeight * 0.58)));
  }
}, { passive: true });

/* ── GALLERY SLIDESHOW ──────────────────────────────── */
(function () {
  const slides = document.querySelectorAll('.gslide');
  const dots   = document.querySelectorAll('.gdot');
  const curEl  = document.getElementById('gCur');
  let current  = 0;
  let timer;

  function goTo(n) {
    slides[current].classList.remove('active');
    slides[current].classList.add('prev');
    setTimeout(() => slides[current].classList.remove('prev'), 1400);
    dots[current].classList.remove('active');
    current = (n + slides.length) % slides.length;
    slides[current].classList.add('active');
    dots[current].classList.add('active');
    curEl.textContent = String(current + 1).padStart(2, '0');
  }

  function next() { goTo(current + 1); resetTimer(); }
  function prev() { goTo(current - 1); resetTimer(); }
  function resetTimer() { clearInterval(timer); timer = setInterval(next, 6000); }

  document.getElementById('gNext').addEventListener('click', next);
  document.getElementById('gPrev').addEventListener('click', prev);
  dots.forEach(d => d.addEventListener('click', () => { goTo(+d.dataset.i); resetTimer(); }));

  // Keyboard navigation
  document.addEventListener('keydown', e => {
    if (e.key === 'ArrowRight') next();
    if (e.key === 'ArrowLeft')  prev();
  });

  // Touch / swipe support
  let touchStartX = 0;
  document.getElementById('galleryStage').addEventListener('touchstart', e => {
    touchStartX = e.touches[0].clientX;
  }, { passive: true });
  document.getElementById('galleryStage').addEventListener('touchend', e => {
    const dx = touchStartX - e.changedTouches[0].clientX;
    if (Math.abs(dx) > 45) { dx > 0 ? next() : prev(); }
  }, { passive: true });

  resetTimer();
})();

/* ── SMOOTH SCROLL ──────────────────────────────────── */
document.querySelectorAll('a[href^="#"]').forEach(a => {
  a.addEventListener('click', e => {
    e.preventDefault();
    const target = document.querySelector(a.getAttribute('href'));
    if (target) target.scrollIntoView({ behavior: 'smooth' });
  });
});
