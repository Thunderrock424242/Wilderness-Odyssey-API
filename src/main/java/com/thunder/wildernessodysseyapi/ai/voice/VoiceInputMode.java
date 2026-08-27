package com.thunder.wildernessodysseyapi.ai.voice;

/** Client input policy for the optional local speech recognizer. */
public enum VoiceInputMode {
    TEXT,
    PUSH_TO_TALK,
    /** Reserved in config, intentionally inactive until privacy and activation UX are implemented. */
    ALWAYS_LISTENING
}
