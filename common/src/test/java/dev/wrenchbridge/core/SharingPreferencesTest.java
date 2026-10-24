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
}
