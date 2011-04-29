package endpoints;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.config.ApplicationName;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.Field;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static endpoints.generated.jooq.Tables.*;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.trueCondition;

/**
 * Values which auto-increment as part of an endpoint execution, but which only auto-increment if they're used.
 */
@RequiredArgsConstructor
public class OnDemandIncrementingNumber {
    
    public enum OnDemandIncrementingNumberType {
        perpetual {
            public @Override @Nonnull Field<Integer> getRequestLogField() { return REQUEST_LOG.ON_DEMAND_PERPETUAL_INCREMENTING_NUMBER; }
            public @Override @Nonnull Condition getRequestLogCondition(Instant now, ZoneId timezone) { return trueCondition(); }
        },
        year {
            public @Override @Nonnull Field<Integer> getRequestLogField() { return REQUEST_LOG.ON_DEMAND_YEAR_INCREMENTING_NUMBER; }
            public @Override @Nonnull Condition getRequestLogCondition(@Nonnull Instant now, @Nonnull ZoneId timezone) { 
                var nowLocal = now.atZone(timezone).toLocalDateTime();
                var startLocal = LocalDateTime.of(nowLocal.getYear(), Month.JANUARY, 1, 0, 0);
                return REQUEST_LOG.DATETIME_UTC.ge(startLocal.atZone(timezone).toInstant())
                    .and(REQUEST_LOG.DATETIME_UTC.lt(startLocal.plus(1, ChronoUnit.YEARS).atZone(timezone).toInstant())); 
            }
        },
        month {
            public @Override @Nonnull Field<Integer> getRequestLogField() { return REQUEST_LOG.ON_DEMAND_MONTH_INCREMENTING_NUMBER; }
            public @Override @Nonnull Condition getRequestLogCondition(@Nonnull Instant now, @Nonnull ZoneId timezone) {
                var nowLocal = now.atZone(timezone).toLocalDateTime();
                var startLocal = LocalDateTime.of(nowLocal.getYear(), nowLocal.getMonth(), 1, 0, 0);
                return REQUEST_LOG.DATETIME_UTC.ge(startLocal.atZone(timezone).toInstant())
                    .and(REQUEST_LOG.DATETIME_UTC.lt(startLocal.plus(1, ChronoUnit.MONTHS).atZone(timezone).toInstant()));
            }
        };

        public abstract @Nonnull Field<Integer> getRequestLogField();
        public abstract @Nonnull Condition getRequestLogCondition(@Nonnull Instant now, @Nonnull ZoneId timezone);
    }

    protected final @Nonnull ApplicationName application;
    protected final @Nonnull PublishEnvironment environment;
    protected final @Nonnull OnDemandIncrementingNumberType type;
    protected final @Nonnull Instant now;
    
    protected @CheckForNull Integer value;
    
    public @CheckForNull Integer getValueOrNull() { return value; }
    
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter") 
    public int getOrFetchValue(@Nonnull DbTransaction tx) {
        synchronized (tx) {
            if (value == null) {
                tx.jooq()
                    .selectFrom(APPLICATION_PUBLISH)
                    .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(application))
                    .and(APPLICATION_PUBLISH.ENVIRONMENT.eq(environment))
                    .forUpdate()
                    .fetchOne();

                var timezone = tx.jooq()
                    .select(APPLICATION_CONFIG.TIMEZONE)
                    .from(APPLICATION_CONFIG)
                    .where(APPLICATION_CONFIG.APPLICATION_NAME.eq(application))
                    .fetchOne().value1();

                var max = tx.jooq()
                    .select(max(type.getRequestLogField()))
                    .from(REQUEST_LOG)
                    .where(REQUEST_LOG.APPLICATION.eq(application))
                    .and(REQUEST_LOG.ENVIRONMENT.eq(environment))
                    .and(type.getRequestLogCondition(now, timezone))
                    .fetchOne().value1();

                if (max == null) value = 1;
                else value = max + 1;
            }

            return value;
        }
    }
    
    public static @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> newLazyNumbers(
        @Nonnull ApplicationName application, @Nonnull PublishEnvironment environment, @Nonnull Instant now
    ) {
        return Arrays.stream(OnDemandIncrementingNumberType.values()).collect(Collectors.toMap(t -> t, 
            t ->new OnDemandIncrementingNumber(application, environment, t, now)));
    }
}
