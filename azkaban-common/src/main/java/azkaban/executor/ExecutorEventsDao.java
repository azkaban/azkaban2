package azkaban.executor;

import azkaban.database.AbstractJdbcLoader;
import azkaban.db.DatabaseOperator;
import azkaban.executor.ExecutorLogEvent.EventType;
import azkaban.metrics.CommonMetrics;
import azkaban.utils.Props;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;

@Singleton
public class ExecutorEventsDao extends AbstractJdbcLoader {

  private final DatabaseOperator dbOperator;

  @Inject
  public ExecutorEventsDao(final Props props, final CommonMetrics commonMetrics,
                           final DatabaseOperator dbOperator) {
    super(props, commonMetrics);
    this.dbOperator = dbOperator;
  }

  public void postExecutorEvent(final Executor executor, final EventType type, final String user,
                                final String message) throws ExecutorManagerException {
    final QueryRunner runner = createQueryRunner();

    final String INSERT_PROJECT_EVENTS =
        "INSERT INTO executor_events (executor_id, event_type, event_time, username, message) values (?,?,?,?,?)";
    final Date updateDate = new Date();
    try {
      runner.update(INSERT_PROJECT_EVENTS, executor.getId(), type.getNumVal(),
          updateDate, user, message);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Failed to post executor event", e);
    }
  }

  public List<ExecutorLogEvent> getExecutorEvents(final Executor executor, final int num,
                                                  final int offset)
      throws ExecutorManagerException {
    final QueryRunner runner = createQueryRunner();

    final ExecutorLogsResultHandler logHandler = new ExecutorLogsResultHandler();
    List<ExecutorLogEvent> events = null;
    try {
      events =
          runner.query(ExecutorLogsResultHandler.SELECT_EXECUTOR_EVENTS_ORDER,
              logHandler, executor.getId(), num, offset);
    } catch (final SQLException e) {
      throw new ExecutorManagerException(
          "Failed to fetch events for executor id : " + executor.getId(), e);
    }

    return events;
  }

  /**
   * JDBC ResultSetHandler to fetch records from executor_events table
   */
  private static class ExecutorLogsResultHandler implements
      ResultSetHandler<List<ExecutorLogEvent>> {

    private static final String SELECT_EXECUTOR_EVENTS_ORDER =
        "SELECT executor_id, event_type, event_time, username, message FROM executor_events "
            + " WHERE executor_id=? ORDER BY event_time LIMIT ? OFFSET ?";

    @Override
    public List<ExecutorLogEvent> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.<ExecutorLogEvent>emptyList();
      }

      final ArrayList<ExecutorLogEvent> events = new ArrayList<>();
      do {
        final int executorId = rs.getInt(1);
        final int eventType = rs.getInt(2);
        final Date eventTime = rs.getDate(3);
        final String username = rs.getString(4);
        final String message = rs.getString(5);

        final ExecutorLogEvent event =
            new ExecutorLogEvent(executorId, username, eventTime,
                EventType.fromInteger(eventType), message);
        events.add(event);
      } while (rs.next());

      return events;
    }
  }
}