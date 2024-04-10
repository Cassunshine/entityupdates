package com.cassunshine.entityupdates;

import net.fabricmc.api.ClientModInitializer;

public class EntityUpdatesClient implements ClientModInitializer {

    public static boolean isEnabled = true;
    public static int maxRenders = 64;

    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
    }
}
