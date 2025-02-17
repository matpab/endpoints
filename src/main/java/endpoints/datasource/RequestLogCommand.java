package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.generated.jooq.tables.records.RequestLogIdsRecord;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static endpoints.generated.jooq.Tables.REQUEST_LOG_IDS;
import static java.time.ZoneOffset.UTC;

public class RequestLogCommand extends DataSourceCommand {

    protected static final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public RequestLogCommand(
        @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element command
    ) throws ConfigurationException {
        super(threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, command);
        assertNoOtherElements(command);
    }

    protected void appendElementWithText(
        @Nonnull Element container, @Nonnull String elementName, @CheckForNull Object contents
    ) {
        if (contents == null) return;

        var result = container.getOwnerDocument().createElement(elementName);
        result.setTextContent(contents.toString());
        container.appendChild(result);
    }

    protected void appendElementWithXmlContents(@Nonnull Element container, @CheckForNull Element contents) {
        if (contents == null) return;
        container.appendChild(container.getOwnerDocument().importNode(contents, true));
    }

    protected @Nonnull Element createRequestLogEntryXml(
        @Nonnull Document doc, @Nonnull RequestLogIdsRecord ids, @Nonnull RequestLogRecord r
    ) {
        var element = doc.createElement("request-log-entry");

        appendElementWithText(element, "request-id", r.getRequestId().id);
        appendElementWithText(element, "endpoint", ids.getEndpoint().name);
        appendElementWithText(element, "datetime-utc", r.getDatetime().atZone(UTC).format(dateTimeFormat));
        appendElementWithText(element, "status-code", r.getStatusCode());
        appendElementWithText(element, "exception-message", r.getExceptionMessage());
        appendElementWithText(element, "incremental-id-per-endpoint", ids.getIncrementalIdPerEndpoint());
        appendElementWithText(element, "random-id-per-application",
            Optional.ofNullable(ids.getRandomIdPerApplication()).map(x -> x.getId()).orElse(null));
        appendElementWithText(element, "user-agent", r.getUserAgent());
        appendElementWithText(element, "environment", ids.getEnvironment());
        appendElementWithText(element, "http-request-failed-url", r.getHttpRequestFailedUrl());
        appendElementWithText(element, "http-request-failed-status-code", r.getHttpRequestFailedStatusCode());
        appendElementWithText(element, "xslt-parameter-error-message", r.getXsltParameterErrorMessage());
        appendElementWithText(element, "on-demand-perpetual-incrementing-number", ids.getOnDemandPerpetualIncrementingNumber());
        appendElementWithText(element, "on-demand-perpetual-month-number", ids.getOnDemandMonthIncrementingNumber());
        appendElementWithText(element, "on-demand-perpetual-year-number", ids.getOnDemandYearIncrementingNumber());
        appendElementWithXmlContents(element, r.getParameterTransformationInput());
        appendElementWithXmlContents(element, r.getParameterTransformationOutput());

        return element;
    }
    
    @Override public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws TransformationFailedException {
        var result = new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                synchronized (context.tx.db) {
                    var doc = DomParser.newDocumentBuilder().newDocument();

                    var root = doc.createElement("request-log");

                    var rows = context.tx.db.jooq()
                        .select()
                        .from(REQUEST_LOG_IDS)
                        .join(REQUEST_LOG).on(REQUEST_LOG.REQUEST_ID.eq(REQUEST_LOG_IDS.REQUEST_ID))
                        .where(REQUEST_LOG_IDS.APPLICATION.eq(context.applicationName))
                        .orderBy(REQUEST_LOG.DATETIME.asc())
                        .fetch();
                    for (var r : rows) root.appendChild(createRequestLogEntryXml(doc, r.into(REQUEST_LOG_IDS), r.into(REQUEST_LOG)));

                    return new Element[] { root };
                }
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
