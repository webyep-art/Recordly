package com.recordly;

import com.recordly.client.RecordlyClientEvents;
import com.recordly.recording.RecordingManager;
import com.recordly.storage.RecordlyStorage;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Recordly.MODID)
public class Recordly {
    public static final String MODID = "recordly";
    private static final Logger LOGGER = LoggerFactory.getLogger(Recordly.class);

    private static RecordlyStorage storage;

    public Recordly(IEventBus modEventBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
            NeoForge.EVENT_BUS.register(RecordlyClientEvents.class);
        }
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        storage = new RecordlyStorage(Minecraft.getInstance().gameDirectory.toPath());
        RecordingManager.getInstance().initialize(storage);
        LOGGER.info("Recordly: Initialized client mod with storage at {}", storage.getStorageDirectory());
    }

    public static RecordlyStorage getStorage() {
        return storage;
    }
}
