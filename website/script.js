/* ═══════════════════════════════════════════════════════
   Wilderness Odyssey — JavaScript
   Author : Thunderrock424242
   Version: 3.0.0

   TO UPDATE VERSIONS:
   Edit WO_CONFIG at the top of this file.
   Add new changelog entries at the TOP of each array.
══════════════════════════════════════════════════════════ */

/* ═══════════════════════════════════════
   VERSION CONFIG — update these values
   whenever you release a new version
═══════════════════════════════════════ */
const WO_CONFIG = {
  modpack: {
    version:     "0.4.2-alpha",
    released:    "2025-11-14",
    mcVersion:   "1.21.1",
    loader:      "NeoForge",
    status:      "ALPHA — active development",
    curseforge:  "https://www.curseforge.com/minecraft",
  },
  website: {
    version:     "3.0.0",
    updated:     "2025-11-14",
    author:      "Thunderrock424242",
  },
  // ── MODPACK CHANGELOG ─────────────────
  // Add newest entry at the TOP of this array
  modpackLog: [
    {
      version:  "0.4.2-alpha",
      date:     "2025-11-14",
      entries:  [
        "Added anomaly saturation system — prolonged exposure now has consequences",
        "New biome: The Hollow Reaches (northwest quadrant)",
        "3 new creature variants near crater zones",
        "Fixed water physics desync on multiplayer servers",
        "Optimised SPH fluid simulation — ~18% performance improvement",
      ]
    },
    {
      version:  "0.4.1-alpha",
      date:     "2025-10-28",
      entries:  [
        "Hotfix: Crater Stalker spawn rate reduced (was overwhelming early-game players)",
        "Fixed bunker door rendering on Intel integrated graphics",
        "Minor terrain generation corrections in river delta biomes",
      ]
    },
    {
      version:  "0.4.0-alpha",
      date:     "2025-10-15",
      entries:  [
        "Major update: Water physics v2 — Gerstner wave system replaced with full SPH simulation",
        "Ocean tides now respond to in-game moon cycle",
        "Added boat physics with buoyancy model",
        "First dimension prototype: The Verdant Beyond (experimental)",
        "5 new passive creatures added to lush biomes",
      ]
    },
    {
      version:  "0.3.0-alpha",
      date:     "2025-09-02",
      entries:  [
        "Initial public alpha release on CurseForge",
        "Core biome system: 8 biomes implemented",
        "Basic creature roster: 12 species",
        "Bunker spawn system and intro sequence",
        "World generation overhaul — meteor impact crater as world centrepoint",
      ]
    },
  ],
  // ── WEBSITE CHANGELOG ─────────────────
  // Add newest entry at the TOP of this array
  websiteLog: [
    {
      version:  "3.0.0",
      date:     "2025-11-14",
      entries:  [
        "Full cinematic redesign — movie trailer aesthetic",
        "Interactive Bunker OS terminal with command system",
        "Survivor Logs section with 6 recovered diary entries",
        "Full-width cinematic gallery slideshow with Ken Burns effect",
        "Version checker and changelog system added to terminal",
        "Scroll progress bar, parallax hero, glitch title effect",
      ]
    },
    {
      version:  "2.0.0",
      date:     "2025-10-01",
      entries:  [
        "Colour theme overhaul — amber/gold palette from logo",
        "Blue wisp + orange ember particle system",
        "Roadmap section with animated status indicators",
      ]
    },
    {
      version:  "1.0.0",
      date:     "2025-09-02",
      entries:  [
        "Initial website launch",
        "Basic hero, features, Discord, and CurseForge links",
      ]
    },
  ],
};

/* CURSOR */
const cur=document.getElementById('cur'),curt=document.getElementById('curt');
let cx=0,cy=0;
document.addEventListener('mousemove',e=>{cx=e.clientX;cy=e.clientY;cur.style.left=cx+'px';cur.style.top=cy+'px'});
setInterval(()=>{curt.style.left=cx+'px';curt.style.top=cy+'px'},90);
document.querySelectorAll('a,button').forEach(el=>{
  el.addEventListener('mouseenter',()=>{cur.style.transform='translate(-50%,-50%) scale(2.2)'});
  el.addEventListener('mouseleave',()=>{cur.style.transform='translate(-50%,-50%) scale(1)'});
});

/* SCROLL PROGRESS */
const prog=document.getElementById('prog');
window.addEventListener('scroll',()=>{
  prog.style.width=(window.scrollY/(document.body.scrollHeight-window.innerHeight)*100)+'%';
},{ passive:true });

/* NAV */
const nav=document.getElementById('nav');
window.addEventListener('scroll',()=>nav.classList.toggle('solid',window.scrollY>60),{passive:true});

/* CINEMATIC BARS OPEN */
window.addEventListener('load',()=>setTimeout(()=>document.getElementById('hero').classList.add('bars-open'),80));

/* HERO CANVAS */
const canvas=document.getElementById('hero-canvas');
const ctx=canvas.getContext('2d');
let W,H,particles=[],stars=[];
function resize(){W=canvas.width=window.innerWidth;H=canvas.height=window.innerHeight}
resize();window.addEventListener('resize',()=>{resize();init()},{passive:true});

function init(){
  particles=[];stars=[];
  for(let i=0;i<250;i++){
    stars.push({x:Math.random()*W,y:Math.random()*H,
      r:Math.random()*1.35+.2,a:Math.random()*.52+.07,
      spd:Math.random()*.014+.003,dir:Math.random()>.5?1:-1});
  }
  for(let i=0;i<72;i++)spawn();
}

function spawn(){
  const wisp=Math.random()>.4;
  particles.push({
    x:Math.random()*W, y:H+Math.random()*110,
    r:wisp?Math.random()*2.3+.7:Math.random()*1.55+.35,
    vx:(Math.random()-.5)*(wisp?.72:.24),
    vy:-(Math.random()*(wisp?.48:.72)+.1),
    a:0, maxA:wisp?Math.random()*.52+.22:Math.random()*.62+.2,
    hue:wisp?196+Math.random()*26:11+Math.random()*15,
    sat:wisp?86:100, lum:wisp?65:60,
    life:0, maxLife:Math.random()*(wisp?570:265)+200,
    glow:wisp?Math.random()*21+9:Math.random()*13+4,
    flicker:!wisp, wisp
  });
}

function draw(){
  ctx.clearRect(0,0,W,H);
  for(const s of stars){
    s.a+=s.spd*s.dir;
    if(s.a>.6||s.a<.04)s.dir*=-1;
    ctx.beginPath();ctx.arc(s.x,s.y,s.r,0,Math.PI*2);
    ctx.fillStyle=`rgba(215,222,240,${s.a})`;ctx.fill();
  }
  for(let i=particles.length-1;i>=0;i--){
    const p=particles[i];p.life++;
    p.x+=p.vx+(p.wisp?Math.sin(p.life*.022)*.54:Math.sin(p.life*.042)*.11);
    p.y+=p.vy+(p.flicker?(Math.random()-.5)*.07:0);
    const pr=p.life/p.maxLife;
    let al=pr<.12?(pr/.12)*p.maxA:pr>.7?((1-pr)/.3)*p.maxA:p.maxA;
    if(p.flicker)al*=.74+Math.random()*.26;
    const g=ctx.createRadialGradient(p.x,p.y,0,p.x,p.y,p.glow);
    g.addColorStop(0,`hsla(${p.hue},${p.sat}%,${p.lum}%,${al*.88})`);
    g.addColorStop(.4,`hsla(${p.hue},${p.sat}%,${p.lum}%,${al*.25})`);
    g.addColorStop(1,`hsla(${p.hue},${p.sat}%,${p.lum}%,0)`);
    ctx.beginPath();ctx.arc(p.x,p.y,p.glow,0,Math.PI*2);ctx.fillStyle=g;ctx.fill();
    ctx.beginPath();ctx.arc(p.x,p.y,p.r,0,Math.PI*2);
    ctx.fillStyle=`hsla(${p.hue},${p.sat}%,83%,${al})`;ctx.fill();
    if(p.life>=p.maxLife){particles.splice(i,1);spawn();}
  }
  requestAnimationFrame(draw);
}
init();draw();

/* SCROLL REVEAL */
const obs=new IntersectionObserver(entries=>{
  entries.forEach(e=>{if(e.isIntersecting)e.target.classList.add('in')});
},{threshold:.11,rootMargin:'0px 0px -50px 0px'});
document.querySelectorAll('.rx').forEach(el=>obs.observe(el));

/* HERO PARALLAX */
window.addEventListener('scroll',()=>{
  const s=window.scrollY,hc=document.querySelector('.h-content');
  if(hc&&s<window.innerHeight){
    hc.style.transform=`translateY(${s*.26}px)`;
    hc.style.opacity=Math.max(0,1-(s/(window.innerHeight*.58)));
  }
},{passive:true});


/* INTERACTIVE TERMINAL */
const termOut = document.getElementById('iterm-output');
const termIn  = document.getElementById('iterm-input');

const COMMANDS = {
  help: () => [
    {c:'tg', t:'Available commands:'},
    {c:'tl', t:'&nbsp; status &nbsp;&nbsp;&nbsp;&nbsp;— current world status report'},
    {c:'tl', t:'&nbsp; scan &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;— scan surrounding area for life'},
    {c:'tl', t:'&nbsp; creatures &nbsp;— known creature database'},
    {c:'tl', t:'&nbsp; dimensions — known dimensional anomalies'},
    {c:'tl', t:'&nbsp; lore &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;— fragment of recovered history'},
    {c:'tl', t:'&nbsp; version &nbsp;&nbsp;— modpack &amp; website version info'},
    {c:'tl', t:'&nbsp; modlog &nbsp;&nbsp;&nbsp;— modpack update changelog'},
    {c:'tl', t:'&nbsp; sitelog &nbsp;&nbsp;— website update changelog'},
    {c:'tl', t:'&nbsp; download &nbsp;— access CurseForge download link'},
    {c:'tl', t:'&nbsp; clear &nbsp;&nbsp;&nbsp;&nbsp;— clear terminal'},
    {c:'tl', t:'&nbsp; help &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;— show this list'},
  ],
  status: () => [
    {c:'tg', t:'WORLD STATUS — DAY 18,262'},
    {c:'tl', t:'&nbsp; Atmosphere ................. BREATHABLE'},
    {c:'tl', t:'&nbsp; Average temp ............... +2.4°C baseline'},
    {c:'tw', t:'&nbsp; Anomalous energy ........... ACTIVE (rising)'},
    {c:'tl', t:'&nbsp; Flora coverage ............. 847% pre-impact'},
    {c:'tw', t:'&nbsp; Known survivors ............ 1 (you)'},
    {c:'tb2', t:'&nbsp; Anomaly saturation ......... CRITICAL'},
    {c:'tr', t:'&nbsp; Threat level ............... EXTREME'},
  ],
  scan: () => [
    {c:'tg', t:'SCANNING AREA (200m radius)...'},
    {c:'tl', t:'&nbsp; Life signatures detected ... 47'},
    {c:'tw', t:'&nbsp; Unknown organism type ...... 12'},
    {c:'tl', t:'&nbsp; Passive entities ........... 31'},
    {c:'tr', t:'&nbsp; Hostile entities ........... 4'},
    {c:'tb2', t:'&nbsp; Anomalous signature ........ 1 (unclassified)'},
    {c:'tw', t:'WARNING: One entity is observing this terminal.'},
  ],
  creatures: () => [
    {c:'tg', t:'CREATURE DATABASE — 1,432 entries (sample):'},
    {c:'tl', t:'&nbsp; [001] Thornwing ............ PASSIVE | Sky'},
    {c:'tw', t:'&nbsp; [047] Mirefiend ............ HOSTILE | Wetlands'},
    {c:'tl', t:'&nbsp; [088] Lumoss ............... PASSIVE | Forests'},
    {c:'tr', t:'&nbsp; [203] Crater Stalker ....... HOSTILE | Impact Zone'},
    {c:'tb2', t:'&nbsp; [419] [REDACTED] .......... UNKNOWN | Everywhere'},
    {c:'tw', t:'WARNING: 891 entries remain unclassified.'},
  ],
  dimensions: () => [
    {c:'tg', t:'DIMENSIONAL ANOMALIES — 3 confirmed:'},
    {c:'tb2', t:'&nbsp; [DIM-1] The Verdant Beyond &nbsp;— STABLE'},
    {c:'tw', t:'&nbsp; [DIM-2] The Ash Realm &nbsp;&nbsp;&nbsp;&nbsp;— UNSTABLE'},
    {c:'tr', t:'&nbsp; [DIM-3] [CLASSIFIED] &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;— DO NOT ENTER'},
    {c:'tl', t:'&nbsp; Entry requires meteor-shard key.'},
    {c:'tw', t:'NOTE: DIM-3 entry has been attempted twice.'},
    {c:'tr', t:'NOTE: Neither explorer returned.'},
  ],
  lore: () => [
    {c:'tg', t:'RECOVERED FRAGMENT — ARCHIVE 7, PAGE 12:'},
    {c:'tl', t:'"The meteor was tracked for 6 years before impact. The energy it carried was detected 3 years out.'},
    {c:'tl', t:'&nbsp;Governments knew. Select individuals were warned.'},
    {c:'tw', t:'&nbsp;The bunkers were not built for survival."'},
    {c:'tl', t:'"They were built to contain what the energy would do to people."'},
    {c:'tr', t:'[REMAINDER OF DOCUMENT CORRUPTED]'},
  ],

  version: () => [
    {c:'tg', t:'═══ VERSION INFORMATION ═══'},
    {c:'tl', t:''},
    {c:'tg', t:'  MODPACK'},
    {c:'tl', t:'  Name .............. Wilderness Odyssey'},
    {c:'tl', t:'  Version ........... ' + WO_CONFIG.modpack.version},
    {c:'tl', t:'  Released .......... ' + WO_CONFIG.modpack.released},
    {c:'tl', t:'  Minecraft ......... ' + WO_CONFIG.modpack.mcVersion},
    {c:'tl', t:'  Loader ............ ' + WO_CONFIG.modpack.loader},
    {c:'tw', t:'  Status ............ ' + WO_CONFIG.modpack.status},
    {c:'tl', t:''},
    {c:'tb2', t:'  WEBSITE'},
    {c:'tl', t:'  Version ........... ' + WO_CONFIG.website.version},
    {c:'tl', t:'  Last updated ....... ' + WO_CONFIG.website.updated},
    {c:'tl', t:'  Author ............ ' + WO_CONFIG.website.author},
    {c:'tl', t:''},
    {c:'tg', t:'  Type "modlog" or "sitelog" for changelogs.'},
  ],
  modlog: () => {
    const lines = [{c:'tg', t:'═══ MODPACK CHANGELOG ═══'},{c:'tl',t:''}];
    WO_CONFIG.modpackLog.forEach(release => {
      lines.push({c:'tw', t:'  ▸ v' + release.version + '  (' + release.date + ')'});
      release.entries.forEach(e => lines.push({c:'tl', t:'    · ' + e}));
      lines.push({c:'tl', t:''});
    });
    return lines;
  },
  sitelog: () => {
    const lines = [{c:'tb2', t:'═══ WEBSITE CHANGELOG ═══'},{c:'tl',t:''}];
    WO_CONFIG.websiteLog.forEach(release => {
      lines.push({c:'tw', t:'  ▸ v' + release.version + '  (' + release.date + ')'});
      release.entries.forEach(e => lines.push({c:'tl', t:'    · ' + e}));
      lines.push({c:'tl', t:''});
    });
    return lines;
  },

  download: () => {
    window.open('https://www.curseforge.com/minecraft','_blank');
    return [{c:'tg', t:'Opening CurseForge... Good luck, Survivor.'}];
  },
  clear: () => { termOut.innerHTML = ''; return []; },
};

function addLine(cls, text){
  const d=document.createElement('div');
  d.className='tl '+cls;d.innerHTML=text;
  termOut.appendChild(d);
  termOut.scrollTop=termOut.scrollHeight;
}

function runCmd(el){
  const cmd = (el.textContent||el).trim().toLowerCase();
  addLine('iterm-cmd','&gt; '+cmd);
  if(COMMANDS[cmd]){
    const lines = COMMANDS[cmd]();
    lines.forEach(l=>setTimeout(()=>addLine(l.c,l.t),50));
  } else {
    addLine('iterm-err','ERROR: Unknown command "'+cmd+'". Type help.');
  }
}

termIn.addEventListener('keydown',e=>{
  if(e.key==='Enter'){
    const val=termIn.value.trim().toLowerCase();
    if(val){ runCmd({textContent:val}); termIn.value=''; }
  }
});
document.getElementById('iterm').addEventListener('click',()=>termIn.focus());


/* GALLERY SLIDESHOW */
(function(){
  const slides = document.querySelectorAll('.gslide');
  const dots   = document.querySelectorAll('.gdot');
  const curEl  = document.getElementById('gCur');
  let current  = 0;
  let timer;

  function goTo(n){
    slides[current].classList.remove('active');
    slides[current].classList.add('prev');
    setTimeout(()=>slides[current].classList.remove('prev'), 1400);
    dots[current].classList.remove('active');
    current = (n + slides.length) % slides.length;
    slides[current].classList.add('active');
    dots[current].classList.add('active');
    curEl.textContent = String(current+1).padStart(2,'0');
  }

  function next(){ goTo(current+1); resetTimer(); }
  function prev(){ goTo(current-1); resetTimer(); }
  function resetTimer(){ clearInterval(timer); timer=setInterval(next, 6000); }

  document.getElementById('gNext').addEventListener('click', next);
  document.getElementById('gPrev').addEventListener('click', prev);
  dots.forEach(d=>d.addEventListener('click',()=>{ goTo(+d.dataset.i); resetTimer(); }));

  /* Keyboard */
  document.addEventListener('keydown',e=>{
    if(e.key==='ArrowRight') next();
    if(e.key==='ArrowLeft')  prev();
  });

  /* Touch/swipe */
  let tx=0;
  document.getElementById('galleryStage').addEventListener('touchstart',e=>tx=e.touches[0].clientX,{passive:true});
  document.getElementById('galleryStage').addEventListener('touchend',e=>{
    const dx=tx-e.changedTouches[0].clientX;
    if(Math.abs(dx)>45){ dx>0?next():prev(); }
  },{passive:true});

  resetTimer();
})();

/* SMOOTH SCROLL */
document.querySelectorAll('a[href^="#"]').forEach(a=>{
  a.addEventListener('click',e=>{
    e.preventDefault();
    const t=document.querySelector(a.getAttribute('href'));
    if(t)t.scrollIntoView({behavior:'smooth'});
  });
});