/*
 * The code of this mod element is always locked.
 *
 * You can register new events in this class too.
 *
 * If you want to make a plain independent class, create it using
 * Project Browser -> New... and make sure to make the class
 * outside net.mcreator.wildernessoddesyapi as this package is managed by MCreator.
 *
 * If you change workspace package, modid or prefix, you will need
 * to manually adapt this file to these changes or remake it.
 *
 * This class will be added in the mod root package.
*/
package net.mcreator.wildernessodysseyapi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class READMEGenerator {

    private static final String README_FILE_PATH = "README.md";

    public static void generateReadme() {
        File readmeFile = new File(README_FILE_PATH);

        // If the README already exists, don't overwrite it.
        if (readmeFile.exists()) {
            return;
        }

        String readmeContent = generateReadmeContent();

        try (FileWriter writer = new FileWriter(readmeFile)) {
            writer.write(readmeContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String generateReadmeContent() {
        StringBuilder content = new StringBuilder();

        content.append("# Anti-Cheat Mod README\n\n");

        // Terms and Conditions Section
        content.append("## Terms and Conditions\n");
        content.append("By using the Anti-Cheat Mod (\"the Mod\"), you agree to the following terms and conditions. The Mod is intended to prevent cheating and enforce fair gameplay across servers.\n\n");
        content.append("1. **Acceptance of Terms**: You must agree to these terms before using the Mod. By installing or running the Mod, you are indicating your acceptance.\n");
        content.append("2. **Scope**: The Mod enforces anti-cheat mechanisms, such as monitoring mod usage, preventing resource pack manipulation, and tracking player behavior.\n");
        content.append("3. **Logging and Data Use**: Player data related to mods and resource packs may be logged locally and, optionally, globally to a GitHub repository.\n");
        content.append("4. **Global Ban Feature**: A player banned by one server can be banned globally if the server is opted-in for global bans.\n");
        content.append("5. **Compliance**: Server administrators are responsible for ensuring that player data is managed in compliance with local laws and regulations.\n");
        content.append("6. **Liability**: The Mod is provided 'as-is'. The creators of the Mod assume no liability for any consequences resulting from its use.\n\n");

        // Privacy Notice Section
        content.append("## Privacy Notice\n");
        content.append("This section describes how player data is collected and managed by the Anti-Cheat Mod.\n\n");
        content.append("1. **Data Collected**: The Mod collects data related to mods and resource packs installed by players to enforce server compliance.\n");
        content.append("2. **Usage of Data**: Data may be logged locally or globally for tracking cheating behavior. Global logging is optional and can be opted-in by server administrators.\n");
        content.append("3. **Data Sharing**: If global logging is enabled, collected data is shared in a central GitHub repository to maintain transparency across participating servers.\n");
        content.append("4. **Player Rights**: Players have the right to request details about any data collected. Server administrators are responsible for responding to these requests appropriately.\n");
        content.append("5. **Data Retention**: Data will remain stored until manually deleted by server administrators or the GitHub repository owner.\n\n");

        // Feature Overview Section
        content.append("## Features of the Anti-Cheat Mod\n\n");
        content.append("### 1. Mod Whitelist Enforcement\n");
        content.append("The Mod enforces a list of required mods that must be installed for players to join the server. If a player does not have the required mods, they will be disconnected.\n\n");

        content.append("### 2. Unauthorized Mod Detection\n");
        content.append("The Mod checks for unauthorized mods that are not part of the whitelisted set. If such mods are detected, the player will be disconnected from the server.\n\n");

        content.append("### 3. Resource Pack Blacklist\n");
        content.append("The Mod checks if players are using resource packs from a blacklist. If blacklisted resource packs are detected, the player will be logged and disconnected.\n\n");

        content.append("### 4. Local and Global Logging\n");
        content.append("The Mod supports logging violations both locally on the server and, optionally, globally to a central GitHub repository. This helps track cheating behavior across multiple servers.\n\n");

        content.append("### 5. Global Ban System\n");
        content.append("The Mod supports a global ban feature, where banned players can be restricted from all participating servers that opt into global bans. Only authorized administrators can use global ban and unban commands.\n\n");

        content.append("### 6. Anti-Cheat Only for Authorized Servers\n");
        content.append("The Mod's anti-cheat features are only enabled for whitelisted servers. If a server is not whitelisted, the Mod will disable its anti-cheat functionality but retain other features.\n\n");

        content.append("### 7. Agreement Enforcement\n");
        content.append("Server administrators must agree to the Terms and Conditions and Privacy Notice in order to use the Mod. Without agreeing, the Mod's anti-cheat features will be disabled.\n\n");

        content.append("## Getting Started\n\n");
        content.append("1. **Installation**: Place the Mod in your server's mods folder.\n");
        content.append("2. **Configuration**: Ensure that your server is whitelisted by the Mod to use anti-cheat features.\n");
        content.append("3. **Opt-in for Global Logging**: If desired, configure your server to log violations globally by enabling the global logging feature in the server settings.\n\n");

        content.append("## Important Notes\n\n");
        content.append("- **Data Privacy**: Server admins are required to inform players of data collection and obtain consent where required by law.\n");
        content.append("- **Limitations**: The Mod is provided as-is and does not guarantee 100% detection of all cheating behavior.\n");

        return content.toString();
    }
}
