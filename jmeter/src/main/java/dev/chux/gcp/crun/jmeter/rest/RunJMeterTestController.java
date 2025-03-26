package dev.chux.gcp.crun.jmeter.rest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletOutputStream;

import com.google.inject.Inject;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Ints;

import spark.Request;
import spark.Response;

import dev.chux.gcp.crun.ConfigService;
import dev.chux.gcp.crun.rest.Route;
import dev.chux.gcp.crun.jmeter.JMeterTestService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.getStackTraceAsString;

import static spark.Spark.*;

public class RunJMeterTestController implements Route {
  private static final Logger logger = LoggerFactory.getLogger(RunJMeterTestController.class);

  public static final String PROPERTY_JMETER_MODES = "jmeter.modes";

  private static final Splitter TRACE_SPLITTER = 
    Splitter.on(CharMatcher.anyOf("/;-")).trimResults().omitEmptyStrings().limit(3);
  
  private static final Splitter.MapSplitter METADATA_SPLITTER =
    Splitter.on(CharMatcher.is(';')).trimResults().omitEmptyStrings().withKeyValueSeparator(':');

  private static final Joiner HEADER_JOINER = Joiner.on('-').skipNulls();

  private static final CharMatcher UNDERSCORE = CharMatcher.is('_');

  private static final String HEADER_PREFIX = "x-jmaas-test";

  private static final String SYS_OUT = "sys";
  private static final String RES_OUT = "res";

  private static final String MODE_QPS = "qps";
  private static final String MODE_CONCURRENCY = "concurrency";

  private static final Integer INTEGER_0 = Integer.valueOf(0);
  private static final Integer INTEGER_1 = Integer.valueOf(1);

  private static final Integer DEFAULT_MIN_LATENCY = Integer.valueOf(1);
  private static final Integer DEFAULT_MAX_LATENCY = Integer.valueOf(1000);

  public static final String DEFAULT_TRACE_ID = "00000000000000000000000000000000";
  private static final String DEFAULT_TRACE_CONTEXT = DEFAULT_TRACE_ID + "/0000000000000000;o=0";

  private final JMeterTestService jMeterTestService;
  private final Set<String> modes;
  private final String instanceID;

  @Inject
  public RunJMeterTestController(
    final ConfigService configService,
    final JMeterTestService jMeterTestService
  ) {
    this.jMeterTestService = jMeterTestService;
    this.modes = this.jmeterModes(configService);
    this.instanceID = this.instanceID(configService);
    logger.info("com.google.cloud.run.instance.id={}", this.instanceID);
  }

  public void register(final String basePath) {
    path(
      basePath, () -> {
        path("/jmeter", () -> {
          path("/test", () -> {
            get("/run", this);
            post("/run", "*/*", this);
          });
        });
      }
    );
  }

  public String endpoint(final String basePath) {
    return "GET " + basePath + "/jmeter/test/run";
  }

  private Set<String> jmeterModes(
    final ConfigService configService
  ) {
    return ImmutableSet.copyOf(jmeterModesProperty(configService));
  }

  private String instanceID(
    final ConfigService configService
  ) {
    return configService
      .getOptionalSysProp("com.google.cloud.run.instance.id")
      .or(this.newInstanceID());
  }

  private String newInstanceID() {
    final UUID uuid = UUID.randomUUID();
    return Hashing.sha256().newHasher()
      .putLong(uuid.getMostSignificantBits())
      .putLong(uuid.getLeastSignificantBits())
      .putLong(System.nanoTime())
      .hash().toString();
  }
  
  private final List<String> jmeterModesProperty(
    final ConfigService configService
  ) {
    return configService.getMultivalueAppProp(PROPERTY_JMETER_MODES);
  }

  private final String toHeaderName(final String param) {
    return HEADER_JOINER.join(
      HEADER_PREFIX,
      UNDERSCORE.replaceFrom(param, '-')
    );
  }

  private Optional<Integer> optionalIntParam(
    final Request request,
    final String param
  ) {
    return fromNullable(
      Ints.tryParse(
        request.queryParamOrDefault(param, "")
      )
    ).or(
      fromNullable(
        Ints.tryParse(
          firstNonNull(
            emptyToNull(
              request.headers(
                toHeaderName(param)
              )
            ), ""
          )
        )
      )
    );
  }

  private int optionalIntParamOr(
    final Request request,
    final String param,
    final Integer defaultValue
  ) {
    return optionalIntParam(request, param).or(defaultValue).intValue();
  }

  private Optional<String> optionalParam(
    final Request request,
    final String param
  ) {
    return fromNullable(
      emptyToNull(
        request.queryParamOrDefault(param, null)
      )
    ).or(
      fromNullable(
        emptyToNull(
          request.headers(
            toHeaderName(param)
          )
        )
      )
    );
  }

  private String optionalParamOr(
    final Request request,
    final String param,
    final String defaultValue
  ) {
    return optionalParam(request, param).or(defaultValue);
  }

  private Map<String, String> metadata(
    final Request request,
    final String param
  ) {
    final Optional<String> metadata = optionalParam(request, param);
    if ( metadata.isPresent() ) {
      return METADATA_SPLITTER.split(metadata.get());
    }
    return ImmutableMap.of();
  }
  
  private String host(final Request request) {
    return optionalParam(request, "host").orNull();
  }

  private Optional<String> id(final Request request) {
    return optionalParam(request, "id");
  }

  private String mode(final Request request) {
    return optionalParamOr(request, "mode", MODE_CONCURRENCY).toLowerCase();
  }

  private String output(final Request request) {
    return optionalParamOr(request, "output", RES_OUT).toLowerCase();
  }

  private Optional<String> test(final Request request) {
    return optionalParam(request, "test");
  }

  private Optional<String> proto(final Request request) {
    return optionalParam(request, "proto");
  }

  private Optional<Integer> port(final Request request) {
    return optionalIntParam(request, "port");
  }

  private Optional<String> endpoint(final Request request) {
    return optionalParam(request, "path");
  }

  private Optional<String> method(final Request request) {
    return optionalParam(request, "method");
  }

  private Optional<String> traceID(final Request request) {
    final Optional<String> xCloudTraceCtx = fromNullable(
      emptyToNull(request.headers("x-cloud-trace-context"))
    );
    final Optional<String> traceparent = fromNullable(
      emptyToNull(request.headers("traceparent"))
    );

    final String traceContext = firstNonNull(
      traceparent.orNull(),
      xCloudTraceCtx.or(DEFAULT_TRACE_CONTEXT)
    );

    final List<String> parts = TRACE_SPLITTER.splitToList(traceContext);
    if ( parts.size() < 2 ) {
      return Optional.of(DEFAULT_TRACE_ID);
    }

    if  ( traceparent.isPresent() ) {
      // trace context extracted from `traceparent`
      // sample: `00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01`
      return fromNullable(emptyToNull(parts.get(1)));
    }
    // trace context extracted from `x-cloud-trace-context`
    return fromNullable(emptyToNull(parts.get(0)));
  }

  private Map<String, String> params(final Request request) {
    return metadata(request, "params");
  }

  private Map<String, String> headers(final Request request) {
    return metadata(request, "headers");
  }

  private Optional<String> body(final Request request) {
    return fromNullable(emptyToNull(request.body()));
  }

  public Object handle(final Request request, final Response response) throws Exception {
    final Optional<String> body = this.body(request);
    final ServletOutputStream responseOutput = response.raw().getOutputStream();

    final String output = this.output(request);
    final Optional<String> traceID = this.traceID(request);
    final Optional<String> id = this.id(request);
    final String testID = id.or(UUID.randomUUID().toString());

    response.type("text/plain");
    response.header(toHeaderName("id"), testID);
    if ( traceID.isPresent() ) {
      response.header("x-cloud-trace-id", traceID.get());
    }

    // FQDN, hostname or IP of the remote service.
    final String host = this.host(request);
    if ( isNullOrEmpty(host) ) {
      response.status(400);
      return "host is required";
    }

    // Operation mode of the Load Test, may be: `qps` or `concurrency`.
    final String mode = this.mode(request);

    if ( isNullOrEmpty(mode) ) {
      halt(400, "mode is required");
      return null;
    }

    if ( !this.modes.contains(mode) ) {
      halt(400, "invalid mode: " + mode);
      return null;
    }

    // test to execute base on the name of JMX files ( case sensitive ).
    final Optional<String> jmx        = this.test(request);

    // may be `http` ot `https` ( case insensitive ).
    final Optional<String> proto      = this.proto(request);

    // HTTP method to use ( case insensitive ).
    final Optional<String> method     = this.method(request);

    // URL path ( aka endpoint ) of the remote service.
    final Optional<String> path       = this.endpoint(request);

    // request metadata ( query params, and headers )
    final Map<String, String> query   = this.params(request);
    final Map<String, String> headers = this.params(request);

    // TCP port where the remote service accepts HTTP requests.
    final Optional<Integer> port      = this.port(request);

    // dynamic test configuration
    final Optional<String> threads    = optionalParam(request, MODE_CONCURRENCY);
    final Optional<String> profile    = optionalParam(request, MODE_QPS);

    if ( (mode.equalsIgnoreCase(MODE_QPS)) && !profile.isPresent() ) {
      halt(400, "parameter 'qps' is required when 'mode' is set to 'qps'");
      return null;
    }

    // expected min/max response time of the service to load test
    final int minLatency  = optionalIntParamOr(request, "min_latency", DEFAULT_MIN_LATENCY);
    final int maxLatency  = optionalIntParamOr(request, "max_latency", DEFAULT_MAX_LATENCY);

    if ( minLatency <= 0 ) {
      halt(400, "'min_latency' must be greater than 0 milli seconds");
      return null;
    }

    if ( maxLatency < minLatency ) {
      halt(400, "'max_latency' must be greater than 'min_latency'");
      return null;
    }

    final int duration    = optionalIntParamOr(request, "duration", INTEGER_0);
    final int concurrency = optionalIntParamOr(request, "concurrency", INTEGER_1);
    final int rampupTime  = optionalIntParamOr(request, "rampup_time", INTEGER_1);
    final int rampupSteps = optionalIntParamOr(request, "rampup_steps", INTEGER_1);

    logger.info(
      toStringHelper(testID)
      .add("instance", this.instanceID)
      .add("output", output)
      .add("test", jmx)
      .add("mode", mode)
      .add("proto", proto)
      .add("method", method)
      .add("host", host)
      .add("port", port)
      .add("path", path)
      .add("query", query)
      .add("headers", headers)
      .add("body", body)
      .add("steps", threads)
      .add("qps", profile)
      .add("duration", duration)
      .add("min_latency", minLatency)
      .add("max_latency", maxLatency)
      .add("concurrency", concurrency)
      .add("rampup_time", rampupTime)
      .add("rampup_steps", rampupSteps)
      .toString()
    );

    responseOutput.println("---- starting: " + testID + " ----");
    logger.info("starting: {}", testID);

    if( output != null && output.equalsIgnoreCase(SYS_OUT) ) {
      this.jMeterTestService.start(
        this.instanceID, testID, traceID,
        jmx, mode, proto, method, host, port, path,
        query, headers, body, threads, profile,
        concurrency, duration, rampupTime, rampupSteps,
        minLatency, maxLatency);
    } else {
      this.jMeterTestService.start(
        this.instanceID, testID, traceID,
        jmx, mode, proto, method, host, port, path,
        query, headers, body, threads, profile,
        concurrency, duration, rampupTime, rampupSteps,
        responseOutput, false /* closeable */,
        minLatency, maxLatency);
    }
    
    logger.info("finished: {}", testID);
    return "---- finished: " + testID + " ----";
  }

}
