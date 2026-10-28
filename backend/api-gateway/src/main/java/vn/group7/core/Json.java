package vn.group7.core;

import com.fasterxml.jackson.databind.*;
import java.util.*;

public final class Json {
  public static final ObjectMapper M = new ObjectMapper();

  public static String text(Object o) {
    try {
      return M.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static Map<String, Object> map(String s) {
    try {
      return M.readValue(
          s, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw ApiError.dependency();
    }
  }

  public static Map<String, Object> obj(Object o) {
    return M.convertValue(
        o, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
  }

  public static Map<String, Object> nullable(Object... pairs) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }
}
