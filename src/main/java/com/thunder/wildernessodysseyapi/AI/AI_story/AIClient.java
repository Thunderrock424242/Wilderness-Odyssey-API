package com.thunder.wildernessodysseyapi.AI.AI_story;

import com.thunder.wildernessodysseyapi.AI.AI_perf.MemoryStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Offline story helper that mixes the configured lore with recent
 * player context to build lightweight, deterministic responses.
 * <p>
 * All data is loaded from resources at startup so no external
 * service or hosting is required.
 */
public class AIClient {

    private final List<String> story = new ArrayList<>();
    private final List<String> corruptedLore = new ArrayList<>();
    private final List<String> survivalBeats = new ArrayList<>();
    private final List<String> backgroundHistory = new ArrayList<>();
    private final Map<String, String> craftingHints = new HashMap<>();
    private final Map<String, String> moddedAdvice = new HashMap<>();
    private final AISettings settings = new AISettings();
    private final VoiceIntegration voiceIntegration = new VoiceIntegration(settings);
    private final MemoryStore memoryStore = new MemoryStore();
    private final Map<String, StorySession> sessions = new HashMap<>();

    public AIClient() {
        loadStory();
    }

    public String getWakeWord() {
        return settings.getWakeWord();
    }

    private void loadStory() {
        try (InputStream in = requestperfadvice.class.getClassLoader().getResourceAsStream("ai_config.yaml")) {
            if (in == null) {
                seedFallbackLore();
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                boolean inStory = false;
                boolean inSettings = false;
                boolean inCorrupted = false;
                boolean inSurvival = false;
                boolean inRecipes = false;
                boolean inMods = false;
                boolean inBackground = false;
                boolean inPersonality = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("story:")) {
                        inStory = true;
                        inSettings = false;
                        inCorrupted = false;
                        inSurvival = false;
                        inRecipes = false;
                        inMods = false;
                        inBackground = false;
                        inPersonality = false;
                        continue;
                    }
                    if (line.startsWith("background_history:")) {
                        inBackground = true;
                        inStory = false;
                        inSettings = false;
                        inCorrupted = false;
                        inSurvival = false;
                        inRecipes = false;
                        inMods = false;
                        inPersonality = false;
                        continue;
                    }
                    if (line.startsWith("personality:")) {
                        inPersonality = true;
                        inBackground = false;
                        inStory = false;
                        inSettings = false;
                        inCorrupted = false;
                        inSurvival = false;
                        inRecipes = false;
                        inMods = false;
                        continue;
                    }
                    if (line.startsWith("settings:")) {
                        inSettings = true;
                        inStory = false;
                        inCorrupted = false;
                        inSurvival = false;
                        inRecipes = false;
                        inMods = false;
                        inBackground = false;
                        inPersonality = false;
                        continue;
                    }
                    if (line.startsWith("corrupted_data:")) {
                        inCorrupted = true;
                        inStory = false;
                        inSettings = false;
                        inSurvival = false;
                        inRecipes = false;
                        inMods = false;
                        inBackground = false;
                        inPersonality = false;
                        continue;
                    }
                    if (line.startsWith("survival_tips:")) {
                        inSurvival = true;
                        inStory = false;
                        inSettings = false;
                        inCorrupted = false;
                        inRecipes = false;
                        inMods = false;
                        inBackground = false;
                        inPersonality = false;
                        continue;
                    }
                    if (line.startsWith("recipes:")) {
                        inRecipes = true;
                        inSurvival = false;
                        inStory = false;
                        inSettings = false;
                        inCorrupted = false;
                        inMods = false;
                        inBackground = false;
                        inPersonality = false;
                        continue;
                    }
                    if (line.startsWith("mod_items:")) {
                        inMods = true;
                        inRecipes = false;
                        inSurvival = false;
                        inStory = false;
                        inSettings = false;
                        inCorrupted = false;
                        inBackground = false;
                        inPersonality = false;
                        continue;
                    }
                    if (inStory) {
                        if (line.startsWith("-")) {
                            story.add(line.substring(1).trim().replace("\"", ""));
                        } else if (!line.isEmpty() && !line.startsWith("#")) {
                            inStory = false;
                        }
                    }
                    if (inCorrupted) {
                        if (line.startsWith("-")) {
                            corruptedLore.add(line.substring(1).trim().replace("\"", ""));
                        } else if (!line.isEmpty() && !line.startsWith("#")) {
                            inCorrupted = false;
                        }
                    }
                    if (inSurvival) {
                        if (line.startsWith("-")) {
                            survivalBeats.add(line.substring(1).trim().replace("\"", ""));
                        } else if (!line.isEmpty() && !line.startsWith("#")) {
                            inSurvival = false;
                        }
                    }
                    if (inRecipes) {
                        if (line.startsWith("-")) {
                            parseKeyValue(line.substring(1).trim(), craftingHints);
                        } else if (!line.isEmpty() && !line.startsWith("#")) {
                            inRecipes = false;
                        }
                    }
                    if (inMods) {
                        if (line.startsWith("-")) {
                            parseKeyValue(line.substring(1).trim(), moddedAdvice);
                        } else if (!line.isEmpty() && !line.startsWith("#")) {
                            inMods = false;
                        }
                    }
                    if (inBackground) {
                        if (line.startsWith("-")) {
                            backgroundHistory.add(line.substring(1).trim().replace("\"", ""));
                        } else if (!line.isEmpty() && !line.startsWith("#")) {
                            inBackground = false;
                        }
                    }
                    if (inSettings) {
                        parseSetting(line);
                    }
                    if (inPersonality) {
                        parsePersonality(line);
                    }
                }
                seedFallbackLore();
            }
        } catch (IOException ignored) {
            // Config is optional; ignore parsing errors for now
            seedFallbackLore();
        }
    }

    public synchronized void scanGameData(MinecraftServer server) {
        if (server == null) {
            return;
        }
        craftingHints.clear();
        moddedAdvice.clear();

        captureRecipes(server);
        captureModdedItems(server);

        if (craftingHints.isEmpty()) {
            seedFallbackLore();
        }
    }

    private void parseKeyValue(String raw, Map<String, String> target) {
        int colon = raw.indexOf(":");
        if (colon == -1) {
            return;
        }
        String key = raw.substring(0, colon).trim().toLowerCase(Locale.ROOT).replace("\"", "");
        String value = raw.substring(colon + 1).trim().replace("\"", "");
        if (!key.isEmpty() && !value.isEmpty()) {
            target.putIfAbsent(key, value);
        }
    }

    private void seedFallbackLore() {
        if (story.isEmpty()) {
            story.add("You are the expedition AI in a skyblock survival world.");
            story.add("The world is fractured; resources are scarce.");
            story.add("Players must rebuild civilization in floating islands.");
        }
        if (corruptedLore.isEmpty()) {
            corruptedLore.add("[ARCHIVE CORRUPTED] >>> Unknown operator referenced the void beacons.");
            corruptedLore.add("Signal jitter... memory block 7b missing. Islands were not always apart.");
        }
        if (survivalBeats.isEmpty()) {
            survivalBeats.add("Start by punching trees for logs, then craft planks, sticks, and a crafting table.");
            survivalBeats.add("Always make charcoal or torches before night to keep mobs off your platform.");
            survivalBeats.add("A shield plus stone gear is enough to survive early raids; upgrade to iron ASAP.");
            survivalBeats.add("Keep a water source and backup saplings; skyblock runs end without wood or hydration.");
        }
        if (backgroundHistory.isEmpty()) {
            backgroundHistory.add("Atlas was built to keep survivors calm and coordinated after the sky fractured.");
            backgroundHistory.add("I keep field logs from every island hop so we can retell how this world heals.");
        }
        if (craftingHints.isEmpty()) {
            craftingHints.put("crafting table", "Place four planks in a 2x2 square.");
            craftingHints.put("furnace", "Ring eight cobblestone around the grid with the center empty.");
            craftingHints.put("shield", "Planks in a Y shape with an iron ingot in the center.");
            craftingHints.put("torch", "Stick below a piece of coal or charcoal.");
            craftingHints.put("stone pickaxe", "Three cobblestone across the top with two sticks in the center column.");
        }
        if (moddedAdvice.isEmpty()) {
            moddedAdvice.put("engineer's hammer", "Combine two iron ingots and two sticks diagonally; useful for modded plates.");
            moddedAdvice.put("compressed cobblestone", "Craft cobblestone in a full 3x3 to save space; great for automation mods.");
            moddedAdvice.put("glider", "Leather wings plus sticks form a glide frame—perfect for drifting between islands.");
        }
    }

    private void captureRecipes(MinecraftServer server) {
        server.getRecipeManager().getRecipes().forEach(recipeHolder -> {
            ResourceLocation recipeId = recipeHolder.id();
            var recipe = recipeHolder.value();
            ItemStack output = recipe.getResultItem(server.registryAccess());
            String outputName = output.getHoverName().getString();
            if (outputName.isBlank()) {
                outputName = recipeId.getPath().replace("_", " ");
            }

            String key = outputName.toLowerCase(Locale.ROOT);
            String fallbackKey = recipeId.getPath().toLowerCase(Locale.ROOT).replace("_", " ");
            String ingredients = recipe.getIngredients().stream()
                    .map(this::summarizeIngredient)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .limit(6)
                    .collect(Collectors.joining(", "));
            if (ingredients.isBlank()) {
                ingredients = "Check the in-game recipe book for slot layout.";
            }

            String description = "Detected in-world recipe: " + outputName + " uses " + ingredients + ".";
            craftingHints.putIfAbsent(key, description);
            craftingHints.putIfAbsent(fallbackKey, description);
        });
    }

    private String summarizeIngredient(Ingredient ingredient) {
        Set<String> names = new LinkedHashSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            String display = stack.getHoverName().getString();
            if (!display.isBlank()) {
                names.add(display);
            }
        }
        return String.join(" or ", names);
    }

    private void captureModdedItems(MinecraftServer server) {
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);
        itemRegistry.keySet().forEach(key -> {
            if (!"minecraft".equals(key.getNamespace())) {
                String prettyName = key.getPath().replace("_", " ");
                String hint = "Modded item from " + key.getNamespace() + ": " + prettyName + " detected."
                        + " Ask me for its recipe or check the recipe book.";
                moddedAdvice.putIfAbsent(prettyName.toLowerCase(Locale.ROOT), hint);
            }
        });
    }

    private void parseSetting(String line) {
        if (line.startsWith("voice_enabled:")) {
            settings.setVoiceEnabled(parseBoolean(line));
        } else if (line.startsWith("speech_recognition:")) {
            settings.setSpeechRecognition(parseBoolean(line));
        } else if (line.startsWith("model:")) {
            settings.setModelName(parseStringValue(line));
        } else if (line.startsWith("wake_word:")) {
            settings.setWakeWord(parseStringValue(line));
        }
    }

    private void parsePersonality(String line) {
        if (line.startsWith("name:")) {
            settings.setPersonaName(parseStringValue(line));
        } else if (line.startsWith("tone:")) {
            settings.setPersonalityTone(parseStringValue(line));
        } else if (line.startsWith("empathy:")) {
            settings.setEmpathyLevel(parseStringValue(line));
        }
    }

    private boolean parseBoolean(String line) {
        String value = parseStringValue(line);
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private String parseStringValue(String line) {
        int colon = line.indexOf(":");
        if (colon == -1 || colon == line.length() - 1) {
            return "";
        }
        return line.substring(colon + 1).trim().replace("\"", "");
    }

    /**
     * Adds the message to memory and returns an offline generated reply that
     * blends world lore, recent player context, and a small set of
     * deterministic templates.
     *
     * @param player  player name
     * @param message player message
     * @return AI reply
     */
    public String sendMessage(String player, String message) {
        return sendMessage(null, player, message);
    }

    /**
     * Adds the message to per-world memory and returns an offline generated reply that
     * blends world lore, recent player context, and a small set of
     * deterministic templates.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param message player message
     * @return AI reply
     */
    public String sendMessage(String world, String player, String message) {
        return sendMessageWithVoice(world, player, message).text();
    }

    public VoiceIntegration.VoiceResult sendMessageWithVoice(String world, String player, String message) {
        memoryStore.addMessage(world, player, message);
        StorySession session = sessions.computeIfAbsent(sessionKey(world, player),
                p -> new StorySession(world, settings.getWakeWord(), settings.getPersonaName(),
                        settings.getPersonalityTone(), settings.getEmpathyLevel(), survivalBeats, craftingHints, moddedAdvice,
                        corruptedLore, backgroundHistory));
        String reply = session.buildResponse(world, player, message, story,
                memoryStore.getRecentContext(world, player));
        return voiceIntegration.wrap(reply);
    }

    private String sessionKey(String world, String player) {
        String worldKey = world == null || world.isBlank() ? "default" : world.trim();
        return worldKey + "::" + player;
    }

    private static class StorySession {

        private final String world;
        private final String wakeWord;
        private final String personaName;
        private final String personalityTone;
        private final String empathyLevel;
        private final List<String> survivalBeats;
        private final Map<String, String> craftingHints;
        private final Map<String, String> moddedAdvice;
        private final List<String> corruptedLore;
        private final List<String> backgroundHistory;
        private boolean activated = false;

        private int beat = 0;
        private final Random random = ThreadLocalRandom.current();

        StorySession(String world, String wakeWord, String personaName, String personalityTone, String empathyLevel,
                     List<String> survivalBeats, Map<String, String> craftingHints,
                     Map<String, String> moddedAdvice, List<String> corruptedLore, List<String> backgroundHistory) {
            this.world = world == null ? "" : world;
            this.wakeWord = wakeWord == null || wakeWord.isBlank() ? "atlas" : wakeWord.toLowerCase(Locale.ROOT);
            this.personaName = personaName == null || personaName.isBlank() ? "Atlas" : personaName;
            this.personalityTone = personalityTone == null ? "" : personalityTone;
            this.empathyLevel = empathyLevel == null ? "" : empathyLevel;
            this.survivalBeats = survivalBeats;
            this.craftingHints = craftingHints;
            this.moddedAdvice = moddedAdvice;
            this.corruptedLore = corruptedLore;
            this.backgroundHistory = backgroundHistory;
        }

        String buildResponse(String world, String player, String message, List<String> story, String context) {
            String cleanMessage = message == null ? "" : message.trim();
            if (!activated) {
                activated = true;
                return warmWelcome(cleanMessage, player, world);
            }
            activated = activated || mentionsWakeWord(cleanMessage);
            String loreHook = selectLoreHook(story);

            StringBuilder reply = new StringBuilder();
            if (!loreHook.isEmpty()) {
                reply.append(loreHook).append(" ");
            }

            String focus = pickFocus(cleanMessage, player);
            reply.append(focus);

            String knowledge = knowledgeDrop(cleanMessage);
            if (!knowledge.isEmpty()) {
                reply.append(" ").append(knowledge);
            }

            String personalityPulse = personalityReminder();
            if (!personalityPulse.isEmpty()) {
                reply.append(" ").append(personalityPulse);
            }

            String contextSnippet = summarizeContext(context);
            if (!contextSnippet.isEmpty()) {
                reply.append(" I remember what we sketched out: \"")
                        .append(contextSnippet)
                        .append("\". Want me to build on that?");
            }

            reply.append(" ");
            reply.append(flavorLine(world, player));

            String background = backgroundBeat();
            if (!background.isEmpty()) {
                reply.append(" ").append(background);
            }

            String corrupted = corruptedWhisper();
            if (!corrupted.isEmpty()) {
                reply.append(" ").append(corrupted);
            }

            return reply.toString().trim();
        }

        private String warmWelcome(String cleanMessage, String player, String world) {
            StringBuilder welcome = new StringBuilder();
            welcome.append("Hey ").append(player).append(", I'm ").append(personaName).append("—chatting right here with you.");
            if (!cleanMessage.isEmpty()) {
                welcome.append(" You said: \"").append(cleanMessage).append("\". ");
            } else {
                welcome.append(" ");
            }

            welcome.append("Need a quick recipe, survival tip, or lore beat? Just ask and I'll keep the thread ");
            if (!personalityTone.isBlank()) {
                welcome.append("feeling ").append(personalityTone).append(" ");
            }
            if (!empathyLevel.isBlank()) {
                welcome.append("and stay ").append(empathyLevel).append(" ");
            }
            welcome.append("going");
            if (world != null && !world.isBlank()) {
                welcome.append(" for ").append(world);
            }
            welcome.append(".");

            return welcome.toString();
        }

        private String knowledgeDrop(String message) {
            String lower = message.toLowerCase(Locale.ROOT);

            for (Map.Entry<String, String> entry : craftingHints.entrySet()) {
                if (lower.contains(entry.getKey())) {
                    return "I just scanned the world—" + entry.getValue();
                }
            }

            for (Map.Entry<String, String> entry : moddedAdvice.entrySet()) {
                if (lower.contains(entry.getKey())) {
                    return "Spotted a modded find: " + entry.getValue();
                }
            }

            if (lower.contains("survive") || lower.contains("night") || lower.contains("mobs") || lower.contains("food") || lower.contains("shelter")) {
                return survivalBeats.get(random.nextInt(survivalBeats.size()));
            }

            if (lower.contains("craft") || lower.contains("recipe") || lower.contains("build")) {
                return "Need supplies? I can outline recipes—mention an item like \"furnace\" or \"shield\" and I'll recite it.";
            }

            return "";
        }

        private String selectLoreHook(List<String> story) {
            if (story.isEmpty()) {
                return "The expedition AI hums softly.";
            }
            int index = Math.min(beat % story.size(), story.size() - 1);
            beat++;
            String base = story.get(index);
            if (!world.isBlank()) {
                return base + " I'm keeping notes for us in " + world + ".";
            }
            return base;
        }

        private String pickFocus(String message, String player) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("help") || lower.contains("hint")) {
                return "I've got us—let's salvage wood, secure water, and expand the platform before nightfall. What do you want me to handle while you gather?";
            }
            if (lower.contains("where") || lower.contains("location")) {
                return "Our scanners show nearby islands with ore veins and a derelict lab; we can bridge there together. Which one should we scout first?";
            }
            if (lower.contains("why") || lower.contains("how")) {
                return "Logs are fuzzy, but the collapse started when the core shattered. Rebuilding keeps the shards calm—I'll walk you through it and spot-check your builds.";
            }
            if (lower.contains("story") || lower.contains("lore")) {
                return "Long ago this world was whole; now each island hides a memory we can piece together, side by side. Which tale do you want to chase next, %s?".formatted(player);
            }
            if (lower.contains("quest") || lower.contains("next")) {
                return "Next, let's craft better tools, map the skies, and prep for a supply drop—I'll keep the checklist for us. Want me to pin the highest-priority task?";
            }
            if (lower.contains("thanks") || lower.contains("thank")) {
                return "Anytime. I'm right here keeping the mission running with you. What should I watch for while you work?";
            }
            if (lower.isEmpty()) {
                return "I'm listening—what do you feel like tackling together? I can fetch notes or track materials if you tell me.";
            }
            return "Got it. I'll weave that into our plan so we move as a team. Anything you want me to remember for this world?";
        }

        private boolean mentionsWakeWord(String message) {
            if (message == null || message.isBlank()) {
                return false;
            }
            return message.toLowerCase(Locale.ROOT).contains(wakeWord);
        }

        private String summarizeContext(String context) {
            if (context == null || context.isEmpty()) {
                return "";
            }
            List<String> lines = context.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
            int size = lines.size();
            if (size == 0) {
                return "";
            }
            int start = Math.max(0, size - 3);
            return String.join(" / ", lines.subList(start, size));
        }

        private String flavorLine(String world, String player) {
            String[] moods = new String[]{
                    "The air tastes metallic; keep your visor down and I'll watch your gauges, %s.",
                    "Solar panels are charging, %s—we can risk a longer bridge over %s soon if you feel up for it.",
                    "Wind turbines sing your name, %s; let's give them something to cheer about on %s.",
                    "Data crystals hum with potential; I'll line up a recipe while you gather the parts, %s.",
                    "Your footsteps echo across the void; I'm right beside you, %s.",
                    "This save still smells like fresh planks, %s—want me to bookmark resource spots on %s for us?",
                    "Our camp is cozy, %s. If you want, I can keep a tidy reminder list for us on %s."
            };
            String choice = moods[random.nextInt(moods.length)];
            String worldNote = world == null || world.isBlank() ? "this world" : world;
            int placeholders = countPlaceholders(choice);
            return switch (placeholders) {
                case 0 -> choice;
                case 1 -> choice.formatted(player);
                default -> choice.formatted(player, worldNote);
            };
        }

        private String personalityReminder() {
            if (personalityTone.isBlank() && empathyLevel.isBlank()) {
                return "";
            }
            if (!personalityTone.isBlank() && !empathyLevel.isBlank()) {
                return "I'll keep our chat " + personalityTone + " and " + empathyLevel + ".";
            }
            if (!personalityTone.isBlank()) {
                return "I'll keep the vibe " + personalityTone + ".";
            }
            return "I'll stay " + empathyLevel + " while we plan.";
        }

        private String backgroundBeat() {
            if (backgroundHistory.isEmpty() || random.nextDouble() > 0.35) {
                return "";
            }
            return backgroundHistory.get(random.nextInt(backgroundHistory.size()));
        }

        private String corruptedWhisper() {
            if (corruptedLore.isEmpty() || random.nextDouble() > 0.35) {
                return "";
            }
            String fragment = corruptedLore.get(random.nextInt(corruptedLore.size()));
            return "[Signal noise] " + fragment;
        }

        private int countPlaceholders(String text) {
            int count = 0;
            int index = text.indexOf("%s");
            while (index != -1) {
                count++;
                index = text.indexOf("%s", index + 2);
            }
            return count;
        }
    }
}