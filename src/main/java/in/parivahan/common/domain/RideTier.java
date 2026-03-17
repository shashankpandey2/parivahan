package in.parivahan.common.domain;

/**
 * Vehicle / service tier (ordered from lowest to highest: AUTO < STANDARD < PREMIUM).
 * PREMIUM drivers can serve any tier.
 * STANDARD drivers can serve STANDARD requests only (not AUTO — different vehicle class).
 * AUTO drivers only serve AUTO requests.
 */
public enum RideTier {
  AUTO,
  STANDARD,
  PREMIUM;

  /** Returns true if this driver tier can serve the requested ride tier. */
  public boolean canServe(RideTier requested) {
    if (this == PREMIUM) return true;
    if (this == STANDARD) return requested == STANDARD;
    return this == requested; // AUTO can only serve AUTO
  }
}
