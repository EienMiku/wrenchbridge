package dev.wrenchbridge.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharingPreferencesTest {
  @TempDir Path directory;
  private final UUID first = UUID.randomUUID(), second = UUID.randomUUID();

  @BeforeEach
  void reset() throws Exception {
    SharingPreferences.clearServer();
    SharingPreferences.load(directory.resolve("client.properties"));
  }

  @Test
  void enabledByDefaultAndLocalSettingSurvivesReload() throws Exception {
    assertTrue(SharingPreferences.clientEnabled());
    SharingPreferences.setClientEnabled(false);
    assertFalse(SharingPreferences.allows(first, true, false));
    SharingPreferences.load(directory.resolve("client.properties"));
    assertFalse(SharingPreferences.clientEnabled());
    SharingPreferences.setClientEnabled(true);
    SharingPreferences.load(directory.resolve("client.properties"));
    assertTrue(SharingPreferences.clientEnabled());
  }

  @Test
  void eachServerPlayerHasAnIndependentSettingAndUniversalBypassesIt() throws Exception {
    SharingPreferences.setClientEnabled(false);
    assertTrue(SharingPreferences.allows(first, false, false));
    SharingPreferences.receive(first, false);
    assertFalse(SharingPreferences.allows(first, false, false));
    assertTrue(SharingPreferences.allows(second, false, false));
    assertTrue(SharingPreferences.allows(first, false, true));
    assertTrue(SharingPreferences.allows(first, true, true));
    SharingPreferences.receive(first, true);
    assertTrue(SharingPreferences.allows(first, false, false));
  }

  @Test
  void logoutAndServerShutdownDiscardConnectionState() {
    SharingPreferences.receive(first, false);
    SharingPreferences.forget(first);
    assertTrue(SharingPreferences.allows(first, false, false));
    SharingPreferences.receive(first, false);
    SharingPreferences.receive(second, false);
    SharingPreferences.clearServer();
    assertTrue(SharingPreferences.allows(first, false, false));
    assertTrue(SharingPreferences.allows(second, false, false));
  }

  @Test
  void unknownValuesKeepDefaultAndUnrelatedPropertiesArePreserved() throws Exception {
    Path config = directory.resolve("client.properties");
    Files.write(config, "shareOtherWrenches=unknown\nfutureSetting=kept\n".getBytes("UTF-8"));
    SharingPreferences.load(config);
    assertTrue(SharingPreferences.clientEnabled());
    SharingPreferences.setClientEnabled(false);
    assertTrue(new String(Files.readAllBytes(config), "UTF-8").contains("futureSetting=kept"));
  }

  @Test
  void failedSaveDoesNotChangePrediction() throws Exception {
    Path parent = directory.resolve("not-a-directory");
    SharingPreferences.load(parent.resolve("client.properties"));
    Files.write(parent, new byte[] {1});
    assertThrows(java.io.IOException.class, () -> SharingPreferences.setClientEnabled(false));
    assertTrue(SharingPreferences.clientEnabled());
  }

  @Test
  void displayOptionsAreIndependentPersistAndResetWithoutChangingServerState() throws Exception {
    assertTrue(SharingPreferences.clientDefaults());
    SharingPreferences.receive(first, false);
    for (SharingPreferences.ClientOption option : SharingPreferences.ClientOption.values()) {
      SharingPreferences.setClientOption(option, false);
      SharingPreferences.load(directory.resolve("client.properties"));
      assertFalse(SharingPreferences.enabled(option));
      assertFalse(SharingPreferences.clientDefaults());
    }
    SharingPreferences.resetClientOptions();
    SharingPreferences.load(directory.resolve("client.properties"));
    assertTrue(SharingPreferences.clientDefaults());
    assertFalse(SharingPreferences.allows(first, false, false));
    assertTrue(SharingPreferences.allows(second, false, false));
  }

  @Test
  void oldSharingConfigKeepsItsValueAndDefaultsNewDisplayOptionsOn() throws Exception {
    Path config = directory.resolve("client.properties");
    Files.write(config, "shareOtherWrenches=false\nfutureSetting=kept\n".getBytes("UTF-8"));
    SharingPreferences.load(config);
    assertFalse(SharingPreferences.clientEnabled());
    assertTrue(SharingPreferences.enabled(SharingPreferences.ClientOption.CONFIGURATOR_OVERLAY));
    assertTrue(SharingPreferences.enabled(SharingPreferences.ClientOption.MENU_ICONS));
    assertTrue(SharingPreferences.enabled(SharingPreferences.ClientOption.MODE_HINTS));
    assertTrue(SharingPreferences.enabled(SharingPreferences.ClientOption.SOURCE_DETAILS));
    SharingPreferences.resetClientOptions();
    assertTrue(new String(Files.readAllBytes(config), "UTF-8").contains("futureSetting=kept"));
  }
}
