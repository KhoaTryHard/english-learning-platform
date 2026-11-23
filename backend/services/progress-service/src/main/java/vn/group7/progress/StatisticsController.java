package vn.group7.progress;

import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Admin statistics surface (#36). Role is enforced by the Gateway assertion in ServiceGuard. */
@RestController
public class StatisticsController {
  private final StatisticsService service;

  public StatisticsController(StatisticsService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/admin/statistics")
  public Map<String, Object> statistics(
      @RequestParam(required = false) String courseId,
      @RequestParam(required = false) String levelId) {
    return service.statistics(courseId, levelId);
  }
}
