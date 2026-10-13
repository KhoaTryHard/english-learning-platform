package vn.group7.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class InputTest {
  @Test
  void unknownFieldsAndUnicode() {
    assertThrows(
        ApiError.class,
        () -> Input.check("CoursePatch", Map.of("courseId", UUID.randomUUID().toString())));
    assertThrows(ApiError.class, () -> Input.check("CoursePatch", Map.of()));
    assertEquals(Input.norm("CAFÉ  book"), Input.norm("cafe\u0301\tbook"));
    assertThrows(
        ApiError.class,
        () ->
            Input.check(
                "SetupWrite", Map.of("courseId", "bad", "levelId", UUID.randomUUID().toString())));
  }

  @Test
  void nullableProfile() {
    assertNull(Input.check("ProfilePatch", Json.nullable("fullName", null)).get("fullName"));
    assertThrows(
        ApiError.class,
        () ->
            Input.check("RegisterRequest", Map.of("email", "bad", "password", "abcdefghijklmno")));
  }
}
