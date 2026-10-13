package vn.group7.user;

import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.group7.core.*;

@Component
public class Db {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;

  public Db(JdbcTemplate j, PlatformTransactionManager t) {
    jdbc = j;
    tx = new TransactionTemplate(t);
  }

  public <T> T tx(Supplier<T> f) {
    try {
      return tx.execute(s -> f.get());
    } catch (DataAccessException e) {
      throw map(e);
    }
  }

  public List<Map<String, Object>> rows(String sql, Object... a) {
    try {
      return jdbc.queryForList(sql, a);
    } catch (DataAccessException e) {
      throw map(e);
    }
  }

  public Map<String, Object> one(String sql, Object... a) {
    var r = rows(sql, a);
    if (r.isEmpty()) throw ApiError.missing();
    return r.getFirst();
  }

  public int run(String sql, Object... a) {
    try {
      return jdbc.update(sql, a);
    } catch (DataAccessException e) {
      throw map(e);
    }
  }

  public long count(String sql, Object... a) {
    var r = one(sql, a);
    return ((Number) r.values().iterator().next()).longValue();
  }

  public static String instant(Object t) {
    return t == null
        ? null
        : t instanceof java.sql.Timestamp s
            ? s.toInstant().toString()
            : t instanceof java.time.OffsetDateTime d ? d.toInstant().toString() : t.toString();
  }

  private ApiError map(DataAccessException e) {
    if (e instanceof DuplicateKeyException)
      return new ApiError(409, "RESOURCE_CONFLICT", "Dữ liệu đã tồn tại");
    if (e instanceof DataIntegrityViolationException)
      return ApiError.bad("resource", "Dữ liệu vi phạm ràng buộc");
    return ApiError.dependency();
  }
}
