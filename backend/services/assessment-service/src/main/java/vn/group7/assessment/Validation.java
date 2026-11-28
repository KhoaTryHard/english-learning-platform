package vn.group7.assessment;

import java.math.BigDecimal;
import java.util.*;
import vn.group7.core.*;

final class Validation {
  static final Set<String> TYPES = Set.of("EXERCISE", "QUIZ", "TEST");

  static void fields(Map<String, Object> b, Set<String> allowed, Set<String> required) {
    if (b == null) throw ApiError.bad("body", "Object required");
    if (!allowed.containsAll(b.keySet())) throw ApiError.bad("body", "Unknown field");
    if (!b.keySet().containsAll(required)) throw ApiError.bad("body", "Missing required field");
  }

  static String text(Object v, int max) {
    if (!(v instanceof String s) || s.isBlank() || s.codePointCount(0, s.length()) > max)
      throw ApiError.bad("text", "Invalid text");
    return s.strip();
  }

  static String uuid(Object v) {
    if (!(v instanceof String s)) throw ApiError.bad("id", "UUID required");
    return Input.id(s).toString();
  }

  static String nullableId(Object v) {
    return v == null ? null : uuid(v);
  }

  static int integer(Object v) {
    try {
      var n = new BigDecimal(v.toString());
      return n.intValueExact();
    } catch (Exception e) {
      throw ApiError.bad("number", "Integer required");
    }
  }

  static int page(Integer p) {
    if (p == null) return 0;
    if (p < 0 || p > 10000000) throw ApiError.bad("page", "Invalid page");
    return p;
  }

  static int size(Integer s) {
    if (s == null) return 20;
    if (s < 1 || s > 100) throw ApiError.bad("size", "Invalid size");
    return s;
  }

  static boolean bool(Object v) {
    if (!(v instanceof Boolean b)) throw ApiError.bad("boolean", "Boolean required");
    return b;
  }

  static BigDecimal points(Object v) {
    if (!(v instanceof Number)) throw ApiError.bad("points", "Number required");
    try {
      var n = new BigDecimal(v.toString());
      if (n.signum() <= 0) throw new IllegalArgumentException();
      return n;
    } catch (Exception e) {
      throw ApiError.bad("points", "Positive finite points required");
    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> object(Object v) {
    if (!(v instanceof Map)) throw ApiError.bad("body", "Object required");
    return (Map<String, Object>) v;
  }

  static List<?> list(Object v) {
    if (!(v instanceof List<?> l)) throw ApiError.bad("body", "Array required");
    return l;
  }

  static Map<String, Object> question(Map<String, Object> b, String id) {
    String type = String.valueOf(b.get("questionType"));
    boolean single = type.equals("SINGLE_CHOICE");
    if (!single && !type.equals("TRUE_FALSE"))
      throw ApiError.bad("questionType", "Unsupported type");
    var fields =
        single
            ? Set.of("questionType", "content", "points", "options", "correctOptionId")
            : Set.of("questionType", "content", "points", "correctAnswer");
    fields(b, fields, fields);
    var q =
        Json.nullable(
            "questionId",
            id,
            "questionType",
            type,
            "content",
            text(b.get("content"), 20000),
            "points",
            points(b.get("points")));
    if (single) {
      var source = list(b.get("options"));
      if (source.size() < 2 || source.size() > 6)
        throw ApiError.bad("options", "Require 2 to 6 options");
      var ids = new HashSet<String>();
      var options = new ArrayList<Map<String, Object>>();
      for (var item : source) {
        var option = object(item);
        fields(option, Set.of("optionId", "text"), Set.of("optionId", "text"));
        String oid = uuid(option.get("optionId"));
        if (!ids.add(oid)) throw ApiError.bad("options", "Duplicate option");
        options.add(Map.of("optionId", oid, "text", text(option.get("text"), 1000)));
      }
      String correct = uuid(b.get("correctOptionId"));
      if (!ids.contains(correct)) throw ApiError.bad("correctOptionId", "Option outside question");
      q.put("options", options);
      q.put("correctOptionId", correct);
    } else q.put("correctAnswer", bool(b.get("correctAnswer")));
    return q;
  }

  static Map<String, Object> publicQuestion(Map<String, Object> q) {
    var p = new LinkedHashMap<>(q);
    p.remove("correctOptionId");
    p.remove("correctAnswer");
    return p;
  }
}
