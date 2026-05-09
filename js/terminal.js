/* ═══════════════════════════════════════════════════════
   Wilderness Odyssey — terminal.js
   Handles the interactive Bunker OS terminal.

   TO ADD A NEW COMMAND:
   1. Add it to the COMMANDS object below
   2. Add a hint line inside the help command
   3. Add a <span class="cmd-hint"> button in index.html
══════════════════════════════════════════════════════════ */

const termOut = document.getElementById('iterm-output');
const termIn  = document.getElementById('iterm-input');

/* ── COMMAND DEFINITIONS ────────────────────────────────
   Each command is a function that returns an array of
   line objects: { c: 'css-class', t: 'text content' }

   Colour classes:
     tg  = gold   (success / headings)
     tw  = ember  (warnings)
     tb2 = blue   (info)
     tr  = red    (danger / errors)
     tl  = muted  (normal text)
─────────────────────────────────────────────────────── */
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

  /* ── VERSION & CHANGELOG COMMANDS ──────────────────────
     These read directly from WO_CONFIG in config.js.
     You never need to edit these functions — just update
     config.js and the output here changes automatically.
  ─────────────────────────────────────────────────────── */
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
    const lines = [
      {c:'tg', t:'═══ MODPACK CHANGELOG ═══'},
      {c:'tl', t:''},
    ];
    WO_CONFIG.modpackLog.forEach(release => {
      lines.push({c:'tw', t:'  ▸ v' + release.version + '  (' + release.date + ')'});
      release.entries.forEach(e => lines.push({c:'tl', t:'    · ' + e}));
      lines.push({c:'tl', t:''});
    });
    return lines;
  },

  sitelog: () => {
    const lines = [
      {c:'tb2', t:'═══ WEBSITE CHANGELOG ═══'},
      {c:'tl',  t:''},
    ];
    WO_CONFIG.websiteLog.forEach(release => {
      lines.push({c:'tw', t:'  ▸ v' + release.version + '  (' + release.date + ')'});
      release.entries.forEach(e => lines.push({c:'tl', t:'    · ' + e}));
      lines.push({c:'tl', t:''});
    });
    return lines;
  },

  download: () => {
    window.open(WO_CONFIG.modpack.curseforge, '_blank');
    return [{c:'tg', t:'Opening CurseForge... Good luck, Survivor.'}];
  },

  clear: () => {
    termOut.innerHTML = '';
    return [];
  },

};

/* ── OUTPUT HELPERS ─────────────────────────────────── */
function addLine(cls, text) {
  const d = document.createElement('div');
  d.className = 'tl ' + cls;
  d.innerHTML = text;
  termOut.appendChild(d);
  termOut.scrollTop = termOut.scrollHeight;
}

function runCmd(el) {
  const cmd = (el.textContent || el).trim().toLowerCase();
  addLine('iterm-cmd', '&gt; ' + cmd);
  if (COMMANDS[cmd]) {
    const lines = COMMANDS[cmd]();
    lines.forEach((l, i) => setTimeout(() => addLine(l.c, l.t), i * 18));
  } else {
    addLine('iterm-err', 'ERROR: Unknown command "' + cmd + '". Type help.');
  }
}

/* ── INPUT LISTENERS ────────────────────────────────── */
termIn.addEventListener('keydown', e => {
  if (e.key === 'Enter') {
    const val = termIn.value.trim().toLowerCase();
    if (val) { runCmd({textContent: val}); termIn.value = ''; }
  }
});

document.getElementById('iterm').addEventListener('click', () => termIn.focus());
