package de.linusdev.oneworldfolder.config;

import de.linusdev.OneWorldFolderModClient;
import de.linusdev.lutils.data.DataBuilder;
import de.linusdev.lutils.data.json.Json;
import de.linusdev.lutils.data.json.JsonListImpl;
import de.linusdev.lutils.data.json.parser.JsonParser;
import de.linusdev.lutils.os.OsType;
import de.linusdev.lutils.os.OsUtils;
import de.linusdev.lutils.other.parser.ParseException;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Config {
    public static final @NotNull JsonParser JSON_PARSER = new JsonParser();

    public static final String CONFIG_DIR_NAME = "config";
    public static final String OWF_CONFIG_DIR_NAME = "oneworldfolder";
    public static final String OWF_CONFIG_FILE_NAME = "oneworldfolder.json";

    public static final String EXTERNAL_SAVES_DIR_KEY = "external_saves_directory";
    public static final String AUTO_DETECT = "--auto-detect";

    public static final String PRIORITY_KEY = "priority";

    public static final String SWAP_OWF_BUTTON_AND_SINGLEPLAYER_BUTTON_OLD_KEY = "replace_owf_and_singleplayer_button";
    public static final String SWAP_OWF_BUTTON_AND_SINGLEPLAYER_BUTTON_KEY = "swap_owf_and_singleplayer_button";
    public static final String REPLACE_SINGLEPLAYER_BUTTON_KEY = "replace_singleplayer_button";
    public static final String ADDITIONAL_PACKS_DIRS_KEY = "additional_resourcepacks_dirs";

    public static @NotNull Config from(@Nullable Path @NotNull ... locations) throws IOException, ParseException {
        Config highestPriority = null;
        for(Path location : locations) {
            if(location == null) continue;

            Path configFile = location
                    .resolve(CONFIG_DIR_NAME)
                    .resolve(OWF_CONFIG_DIR_NAME)
                    .resolve(OWF_CONFIG_FILE_NAME);

            if(Files.exists(configFile)) {
                Config c = new Config(configFile);
                c.store(); // add missing fields

                if(highestPriority == null || highestPriority.getPriority() < c.getPriority()) {
                    highestPriority = c;
                }
            }
        }

        if(highestPriority != null)
            return highestPriority;

        return new Config(
                Minecraft.getInstance().gameDirectory.toPath()
                        .resolve(CONFIG_DIR_NAME)
                        .resolve(OWF_CONFIG_DIR_NAME)
                        .resolve(OWF_CONFIG_FILE_NAME)
        ).store();
    }

    private final @NotNull Path configFile;
    private final boolean supportsCustomLevelStorage;
    private boolean cannotFindMinecraftFolder;
    private boolean autodetectSavesPath = false;

    private final Path externalMinecraftDirectory;
    private final String externalSavesDirName;
    private final int priority;
    private final boolean swapOwfButtonAndSingleplayerButton;
    private final boolean replaceSingleplayerButton;
    private final List<String> additionalPackDirs;

    public Config(@NotNull Path configFile) throws IOException, ParseException {
        this.configFile = configFile;

        Json data;
        if(Files.exists(configFile))
            data = JSON_PARSER.parseStream(Files.newInputStream(configFile));
        else
            data = new JsonListImpl(new ArrayList<>(0));

        Path externalSavesDir = data
                .grab(EXTERNAL_SAVES_DIR_KEY)
                .castAndConvert((String string) -> {
                    if (string == null || string.equals(AUTO_DETECT)) {
                        autodetectSavesPath = true;
                        Path md = OneWorldFolderModClient.getDefaultMinecraftFolder();
                        return md == null ? null : md.resolve("saves");
                    }

                    return Paths.get(expandEnvVars(string));

                })
                .get();

        externalMinecraftDirectory = externalSavesDir == null ? null : externalSavesDir.getParent();
        externalSavesDirName = externalSavesDir == null ? null : externalSavesDir.getFileName().toString();
        priority = data.getNumberOrDefault(PRIORITY_KEY, -1).intValue();
        boolean oldSwapOwfButtonAndSingleplayerButton = data.getAsOrDefault(SWAP_OWF_BUTTON_AND_SINGLEPLAYER_BUTTON_OLD_KEY, false);
        swapOwfButtonAndSingleplayerButton = data.getAsOrDefault(SWAP_OWF_BUTTON_AND_SINGLEPLAYER_BUTTON_KEY, oldSwapOwfButtonAndSingleplayerButton);
        replaceSingleplayerButton = data.getAsOrDefault(REPLACE_SINGLEPLAYER_BUTTON_KEY, false);

        supportsCustomLevelStorage = !(externalMinecraftDirectory == null || !Files.exists(externalMinecraftDirectory));
        cannotFindMinecraftFolder = externalMinecraftDirectory == null;

        if(data.get(ADDITIONAL_PACKS_DIRS_KEY) != null) {
            additionalPackDirs = data.grab(ADDITIONAL_PACKS_DIRS_KEY).asList().<String>cast().get();
        } else {
            additionalPackDirs = List.of();
        }

    }

    public @NotNull String getDebugString() {
        String debug = "Config debug info:";
        debug += "\nconfig location: " + configFile;
        debug += "\nexternalMinecraftDirectory: " + externalMinecraftDirectory;
        debug += "\nexists(externalMinecraftDirectory): " + Files.exists(externalMinecraftDirectory);
        debug += "\nexternalSavesDirName: " + externalSavesDirName;
        debug += "\nsupportsCustomLevelStorage: " + supportsCustomLevelStorage;
        debug += "\ncannotFindMinecraftFolder: " + cannotFindMinecraftFolder;
        debug += "\nadditionalPackDirs: " + additionalPackDirs;

        return debug;
    }

    // Source: https://stackoverflow.com/a/15365315/23894947
    private static final Map<String, String> envMap = System.getenv();
    private static @Nullable String linuxHome = null;
    static {
        if (OsUtils.CURRENT_OS == OsType.LINUX) {
            linuxHome = System.getProperty("user.home");

            if(linuxHome.endsWith("/"))
                linuxHome = linuxHome.substring(0, linuxHome.length()-1);
        }
    }

    public static String expandEnvVars(String text) {
       for (Map.Entry<String, String> entry : envMap.entrySet()) {
           String key = entry.getKey();
           String value = entry.getValue();
           text = text.replaceAll("\\$\\{" + key + "\\}", value);
       }

        if (linuxHome != null && text.startsWith("~")) {
            text = text.replace("~", linuxHome);
        }

       return text;
    }

    public boolean isSupportsCustomLevelStorage() {
        return supportsCustomLevelStorage;
    }

    public boolean isCannotFindMinecraftFolder() {
        return cannotFindMinecraftFolder;
    }

    public Path getExternalMinecraftDirectory() {
        return externalMinecraftDirectory;
    }

    public String getExternalSavesDirName() {
        return externalSavesDirName;
    }

    public boolean isSwapOwfButtonAndSingleplayerButton() {
        return swapOwfButtonAndSingleplayerButton;
    }

    public boolean isReplaceSingleplayerButton() {
        return replaceSingleplayerButton;
    }

    public int getPriority() {
        return priority;
    }

    public @NotNull Path getConfigFile() {
        return configFile;
    }

    public List<String> getAdditionalPackDirs() {
        return additionalPackDirs;
    }

    public Config store() throws IOException {
        if(!Files.exists(configFile.getParent()))
            Files.createDirectories(configFile.getParent());

        DataBuilder data = DataBuilder.orderedKnownSize(10)
                .add(EXTERNAL_SAVES_DIR_KEY,
                        autodetectSavesPath ? AUTO_DETECT :
                                externalMinecraftDirectory.resolve(externalSavesDirName).toString()
                )
                .add(PRIORITY_KEY, priority)
                .add(SWAP_OWF_BUTTON_AND_SINGLEPLAYER_BUTTON_KEY, swapOwfButtonAndSingleplayerButton)
                .add(REPLACE_SINGLEPLAYER_BUTTON_KEY, replaceSingleplayerButton)
                .add(ADDITIONAL_PACKS_DIRS_KEY, additionalPackDirs);

        Writer writer = Files.newBufferedWriter(configFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        JSON_PARSER.writeData(writer, data);
        writer.close();
        return this;
    }
}
