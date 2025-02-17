package endpoints;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.config.ApplicationName;
import lombok.Value;

import javax.annotation.Nonnull;

import java.io.Serializable;

import static endpoints.generated.jooq.Tables.REQUEST_LOG_IDS;
import static org.apache.commons.lang.RandomStringUtils.random;
import static org.apache.commons.lang.RandomStringUtils.randomNumeric;

@Value
public class RandomRequestId implements Serializable {
    
    long id;

    /**
     * Note: Assumes application is "locked" i.e. only one instance of this code is running at once
     */
    protected static @Nonnull RandomRequestId generate(
        @Nonnull DbTransaction tx,
        @Nonnull ApplicationName applicationName, @Nonnull PublishEnvironment environment
    ) {
        for (int attempt = 0; attempt < 100; attempt++) {
            // 10 digits, starting with a non-zero so it is always 10 characters long
            var candidate = new RandomRequestId(Long.parseLong(random(1, "123456789") + randomNumeric(9)));

            var existingCount = tx.jooq()
                .selectCount()
                .from(REQUEST_LOG_IDS)
                .where(REQUEST_LOG_IDS.APPLICATION.eq(applicationName))
                .and(REQUEST_LOG_IDS.ENVIRONMENT.eq(environment))
                .and(REQUEST_LOG_IDS.RANDOM_ID_PER_APPLICATION.eq(candidate))
                .fetchOne().value1();
            if (existingCount == 0) return candidate;
        }

        throw new RuntimeException("Cannot find new random number");
    }
}
