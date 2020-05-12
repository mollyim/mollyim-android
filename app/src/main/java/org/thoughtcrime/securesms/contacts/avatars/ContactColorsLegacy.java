package org.thoughtcrime.securesms.contacts.avatars;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Used for migrating legacy colors to modern colors. For normal color generation, use
 * {@link ContactColors}.
 */
public class ContactColorsLegacy {

  private static final String[] LEGACY_PALETTE_2 = new String[]{
      "pink",
      "red",
      "orange",
      "purple",
      "blue",
      "indigo",
      "green",
      "light_green",
      "teal",
      "brown",
      "blue_grey"
  };

  public static MaterialColor generateForV2(@NonNull String name) {
    String serialized = LEGACY_PALETTE_2[Math.abs(name.hashCode()) % LEGACY_PALETTE_2.length];
    try {
      return MaterialColor.fromSerialized(serialized);
    } catch (MaterialColor.UnknownColorException e) {
      return ContactColors.generateFor(name);
    }
  }
}
