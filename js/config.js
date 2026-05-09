/* ═══════════════════════════════════════════════════════
   Wilderness Odyssey — config.js
   Author  : Thunderrock424242

   THIS IS THE ONLY FILE YOU NEED TO EDIT FOR UPDATES.

   HOW TO UPDATE THE MODPACK VERSION:
   1. Change modpack.version to your new version number
   2. Change modpack.released to today's date
   3. Add a new entry at the TOP of modpackLog[]

   HOW TO UPDATE THE WEBSITE VERSION:
   1. Change website.version to your new version number
   2. Change website.updated to today's date
   3. Add a new entry at the TOP of websiteLog[]
══════════════════════════════════════════════════════════ */

const WO_CONFIG = {

  /* ── MODPACK INFO ─────────────────────────────────────
     Update these every time you ship a new modpack build  */
  modpack: {
    version:    "0.4.2-alpha",
    released:   "2025-11-14",
    mcVersion:  "1.21.1",
    loader:     "NeoForge",
    status:     "ALPHA — active development",
    curseforge: "https://www.curseforge.com/minecraft",
  },

  /* ── WEBSITE INFO ─────────────────────────────────────
     Update these every time you change the site          */
  website: {
    version: "0.5.0",
    updated: "5-9-2026",
    author:  "Thunderrock424242",
  },

  /* ── MODPACK CHANGELOG ────────────────────────────────
     ADD NEWEST RELEASE AT THE TOP of this array.
     Copy the block below and paste it above the others:

     {
       version: "0.5.0-alpha",
       date:    "YYYY-MM-DD",
       entries: [
         "What you added or fixed",
         "Another change",
       ]
     },
  ─────────────────────────────────────────────────────── */
  modpackLog: [
    {
      version: "0.4.2-alpha",
      date:    "2025-11-14",
      entries: [
        "Added anomaly saturation system — prolonged exposure now has consequences",
        "New biome: The Hollow Reaches (northwest quadrant)",
        "3 new creature variants near crater zones",
        "Fixed water physics desync on multiplayer servers",
        "Optimised SPH fluid simulation — ~18% performance improvement",
      ]
    },
  ],

  /* ── WEBSITE CHANGELOG ────────────────────────────────
     ADD NEWEST RELEASE AT THE TOP of this array.
     Same format as modpackLog above.
  ─────────────────────────────────────────────────────── */
  websiteLog: [
    {
      version: "0.5.0",
      date:    "5-9-2026",
      entries: [
        "Full cinematic redesign — movie trailer aesthetic",
        "Interactive Bunker OS terminal with command system",
        "Survivor Logs section with 6 recovered diary entries",
        "Full-width cinematic gallery slideshow with Ken Burns effect",
        "Version checker and changelog system added to terminal",
        "Scroll progress bar, parallax hero, glitch title effect",
        "Split into index.html + style.css + js/config.js + js/terminal.js + js/main.js",
      ]
    },
    
  ],

};
