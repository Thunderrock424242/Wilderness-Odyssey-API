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
    version: "3.0.0",
    updated: "2025-11-14",
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
    {
      version: "0.4.1-alpha",
      date:    "2025-10-28",
      entries: [
        "Hotfix: Crater Stalker spawn rate reduced (was overwhelming early-game players)",
        "Fixed bunker door rendering on Intel integrated graphics",
        "Minor terrain generation corrections in river delta biomes",
      ]
    },
    {
      version: "0.4.0-alpha",
      date:    "2025-10-15",
      entries: [
        "Major update: Water physics v2 — Gerstner wave system replaced with full SPH simulation",
        "Ocean tides now respond to in-game moon cycle",
        "Added boat physics with buoyancy model",
        "First dimension prototype: The Verdant Beyond (experimental)",
        "5 new passive creatures added to lush biomes",
      ]
    },
    {
      version: "0.3.0-alpha",
      date:    "2025-09-02",
      entries: [
        "Initial public alpha release on CurseForge",
        "Core biome system: 8 biomes implemented",
        "Basic creature roster: 12 species",
        "Bunker spawn system and intro sequence",
        "World generation overhaul — meteor impact crater as world centrepoint",
      ]
    },
  ],

  /* ── WEBSITE CHANGELOG ────────────────────────────────
     ADD NEWEST RELEASE AT THE TOP of this array.
     Same format as modpackLog above.
  ─────────────────────────────────────────────────────── */
  websiteLog: [
    {
      version: "3.0.0",
      date:    "2025-11-14",
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
    {
      version: "2.0.0",
      date:    "2025-10-01",
      entries: [
        "Colour theme overhaul — amber/gold palette from logo",
        "Blue wisp + orange ember particle system",
        "Roadmap section with animated status indicators",
      ]
    },
    {
      version: "1.0.0",
      date:    "2025-09-02",
      entries: [
        "Initial website launch",
        "Basic hero, features, Discord, and CurseForge links",
      ]
    },
  ],

};
