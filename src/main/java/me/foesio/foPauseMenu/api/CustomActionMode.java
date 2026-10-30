package me.foesio.foPauseMenu.api;

/** How a registered Java handler combines with the button's configured action. */
public enum CustomActionMode {
    /** Run the configured action first, then the Java handler. */
    AFTER_CONFIGURED,
    /** Run only the Java handler. */
    REPLACE_CONFIGURED
}
