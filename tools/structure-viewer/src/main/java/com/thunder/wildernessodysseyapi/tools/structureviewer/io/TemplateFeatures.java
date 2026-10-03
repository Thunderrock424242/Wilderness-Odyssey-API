package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import java.util.*;

/** Conservative library hints; some mods register natural decorations as ordinary structures. */
final class TemplateFeatures {
    private static final Set<String> BUILDINGS=Set.of("house","houses","hut","huts","cabin","camp","castle","fort","fortress","keep",
            "temple","tower","ruin","ruins","dungeon","village","town","city","bunker","base","building","bridge","well","farm",
            "ship","shipwreck","mine","tomb","pyramid","altar","shrine","monument","portal","statue","fountain");
    private static final Set<String> FOLDERS=Set.of("feature","features","decoration","decorations","small_decoration","small_decorations",
            "vegetation","flora","trees","rocks","boulders","cactus","cacti","shrubs");
    private static final Set<String> NATURAL=Set.of("cactus","cacti","rock","rocks","boulder","boulders","tree","trees","log","logs",
            "bush","bushes","shrub","shrubs","mushroom","mushrooms","stump","stumps","palm","palms");
    private static final Set<String> MODIFIERS=Set.of("small","medium","large","big","giant","huge","tall","short","dead","fallen",
            "standing","hollow","damaged","mossy","snowy","snow","oak","dark","spruce","birch","acacia","jungle","forest","pine",
            "desert","sandy","sandstone","red","brown","cobblestone","stone","andesite","granite","diorite","deepslate","blackstone",
            "basalt","obsidian","gravel","sand","patch","cluster","bundle","nether","cap");
    private TemplateFeatures() {}

    static boolean isFeature(String path) {
        String[] parts=path.replace('\\','/').toLowerCase(Locale.ROOT).split("/");
        String filename=parts[parts.length-1].replaceFirst("\\.(nbt|json)$","");
        List<String> words=Arrays.stream(filename.split("[_\\-\\s0-9]+")).filter(word->!word.isEmpty()).toList();
        // A mushroom house or cactus temple remains useful even inside a decoration folder.
        if(words.stream().anyMatch(BUILDINGS::contains))return false;
        int start=0;
        for(int i=0;i<parts.length-1;i++)if(parts[i].equals("structure")||parts[i].equals("structures")){start=i+1;break;}
        for(int i=start;i<parts.length-1;i++)if(FOLDERS.contains(parts[i]))return true;
        return words.stream().anyMatch(NATURAL::contains)&&words.stream().allMatch(word->NATURAL.contains(word)||MODIFIERS.contains(word));
    }
}
