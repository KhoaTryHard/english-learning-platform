package vn.group7.core;

import java.util.*;

public final class Input {
  public static UUID id(String value) {
    try {
      var id = UUID.fromString(value);
      if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
      return id;
    } catch (Exception e) {
      throw ApiError.bad("id", "Invalid UUID");
    }
  }
}
