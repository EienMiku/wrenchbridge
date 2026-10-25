package dev.wrenchbridge;

/** Internal per-item gate. Tag bindings and player-held item identities are never replaced. */
public interface WrenchTraitScope {
  int UNIVERSAL = 1;

  int wrenchbridge$traitState();

  void wrenchbridge$markUniversal();

  void wrenchbridge$retainTraits();

  void wrenchbridge$releaseTraits();
}
