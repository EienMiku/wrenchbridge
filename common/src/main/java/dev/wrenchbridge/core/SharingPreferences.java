package dev.wrenchbridge.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-owned preference, with a separate, connection-scoped server view for each player. */
public final class SharingPreferences {
  public static final String KEY = "shareOtherWrenches";
  private static final Map<UUID, Boolean> SERVER = new ConcurrentHashMap<UUID, Boolean>();

  public enum ClientOption {
    OTHER_WRENCHES(KEY, "other_wrenches"),
    CONFIGURATOR_OVERLAY("showConfiguratorOverlay", "configurator_overlay"),
    MENU_ICONS("showMenuIcons", "menu_icons"),
    MODE_HINTS("showModeHints", "mode_hints");
    public final String property, translationKey, hintKey;

    ClientOption(String property, String translation) {
      this.property = property;
      translationKey = "setting.wrenchbridge." + translation;
      hintKey = translationKey + "_hint";
    }
  }

  // Publish a complete snapshot after a successful save; readers never see a partial reset.
  private static volatile Map<ClientOption, Boolean> clientOptions = readOptions(new Properties());
  private static Path file;
  private static Properties properties = new Properties();

  private SharingPreferences() {}

  public static boolean clientEnabled() {
    return enabled(ClientOption.OTHER_WRENCHES);
  }

  public static boolean enabled(ClientOption option) {
    return Boolean.TRUE.equals(clientOptions.get(option));
  }

  public static boolean clientDefaults() {
    for (ClientOption option : ClientOption.values()) if (!enabled(option)) return false;
    return true;
  }

  public static boolean allows(UUID player, boolean clientSide, boolean universal) {
    if (universal) return true;
    return clientSide ? clientEnabled() : !Boolean.FALSE.equals(SERVER.get(player));
  }

  /**
   * The packet handler supplies the authenticated sender; no player ID is accepted from a client.
   */
  public static void receive(UUID sender, boolean enabled) {
    if (enabled) SERVER.remove(sender);
    else SERVER.put(sender, false);
  }

  public static void forget(UUID player) {
    SERVER.remove(player);
  }

  public static void clearServer() {
    SERVER.clear();
  }

  public static synchronized void load(Path configFile) throws IOException {
    file = configFile.toAbsolutePath().normalize();
    properties = new Properties();
    clientOptions = readOptions(properties);
    if (!Files.exists(file)) return;
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    }
    clientOptions = readOptions(properties);
  }

  public static synchronized void setClientEnabled(boolean enabled) throws IOException {
    setClientOption(ClientOption.OTHER_WRENCHES, enabled);
  }

  public static synchronized void setClientOption(ClientOption option, boolean enabled)
      throws IOException {
    Properties next = new Properties();
    next.putAll(properties);
    next.setProperty(option.property, Boolean.toString(enabled));
    save(next);
  }

  public static synchronized void resetClientOptions() throws IOException {
    Properties next = new Properties();
    next.putAll(properties);
    for (ClientOption option : ClientOption.values()) next.setProperty(option.property, "true");
    save(next);
  }

  private static Map<ClientOption, Boolean> readOptions(Properties values) {
    Map<ClientOption, Boolean> result = new EnumMap<ClientOption, Boolean>(ClientOption.class);
    for (ClientOption option : ClientOption.values())
      result.put(
          option, !"false".equalsIgnoreCase(values.getProperty(option.property, "true").trim()));
    return result;
  }

  private static void save(Properties next) throws IOException {
    if (file == null) throw new IOException("Client preference file has not been initialized");
    Files.createDirectories(file.getParent());
    Path temporary = Files.createTempFile(file.getParent(), "wrenchbridge-client-", ".tmp");
    try {
      try (OutputStream output = Files.newOutputStream(temporary)) {
        next.store(output, "Wrench Bridge client preferences");
      }
      try {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
      properties = next;
      clientOptions = readOptions(next);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
