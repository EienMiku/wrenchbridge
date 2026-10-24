package dev.wrenchbridge.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-owned preference, with a separate, connection-scoped server view for each player. */
public final class SharingPreferences {
  public static final String KEY = "shareOtherWrenches";
  private static final Map<UUID, Boolean> SERVER = new ConcurrentHashMap<UUID, Boolean>();
  private static volatile boolean clientEnabled = true;
  private static Path file;
  private static Properties properties = new Properties();

  private SharingPreferences() {}

  public static boolean clientEnabled() {
    return clientEnabled;
  }

  public static boolean allows(UUID player, boolean clientSide, boolean universal) {
    if (universal) return true;
    return clientSide ? clientEnabled : !Boolean.FALSE.equals(SERVER.get(player));
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
    clientEnabled = true;
    if (!Files.exists(file)) return;
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    }
    clientEnabled = !"false".equalsIgnoreCase(properties.getProperty(KEY, "true").trim());
  }

  public static synchronized void setClientEnabled(boolean enabled) throws IOException {
    if (file == null) throw new IOException("Client preference file has not been initialized");
    Properties next = new Properties();
    next.putAll(properties);
    next.setProperty(KEY, Boolean.toString(enabled));
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
      clientEnabled = enabled;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
