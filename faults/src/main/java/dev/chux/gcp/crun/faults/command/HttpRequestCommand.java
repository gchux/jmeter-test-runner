package dev.chux.gcp.crun.faults.command;

import java.io.OutputStream;

import java.util.Collection;
import java.util.Map;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.assistedinject.Assisted;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import ch.vorburger.exec.ManagedProcess;
import ch.vorburger.exec.ManagedProcessBuilder;
import ch.vorburger.exec.ManagedProcessException;

import dev.chux.gcp.crun.faults.binary.AbstractBinary;
import dev.chux.gcp.crun.faults.binary.CurlFactory;
import dev.chux.gcp.crun.faults.binary.CurlModule;
import dev.chux.gcp.crun.model.HttpRequest;
import dev.chux.gcp.crun.process.ManagedProcessProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Throwables.getStackTraceAsString;

public class HttpRequestCommand implements FaultCommand<HttpRequest> {

  private static final Logger logger = LoggerFactory.getLogger(HttpRequestCommand.class);

  final static String KEY = CommandModule.NAMESPACE + "/http/request";

  private static final String DEFAULT_RUNTIME = "linux";

  private final CurlFactory curlFactory;
  private final Map<String, AbstractBinary<HttpRequest>> curlBinaries;
  private final HttpRequest request;
  private final Optional<String> runtime;
  private final Optional<OutputStream> stdout, stderr;

  @AssistedInject
  public HttpRequestCommand(
    final CurlFactory curlFactory,
    // @Named("faults://binaries/curl")
    @Named(CurlModule.NAMESPACE)
    final Map<String, AbstractBinary<HttpRequest>> curlBinaries,
    @Assisted final HttpRequest request,
    @Assisted("runtime") final Optional<String> runtime,
    @Assisted("stdout") final Optional<OutputStream> stdout,
    @Assisted("stderr") final Optional<OutputStream> stderr
  ) {
    this.curlFactory = curlFactory;
    this.curlBinaries = curlBinaries;
    this.request = request;
    this.runtime = runtime;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  @Override
  public HttpRequest get() {
    return this.request;
  }

  @Override
  public ManagedProcessBuilder getBuilder() throws ManagedProcessException {
    return this.curlBinaries.get(this.runtimeID())
      .getBuilder(this.request, this.stdout, this.stderr);
  } 

  @Override
  public Collection<ManagedProcessProvider> getProviders() {
    return ImmutableList.of(
      this.curlFactory.newCurlLinux(this.request, this.stdout, this.stderr),
      this.curlFactory.newCurlJava(this.request, this.stdout, this.stderr),
      this.curlFactory.newCurlPython(this.request, this.stdout, this.stderr),
      this.curlFactory.newCurlNodeJS(this.request, this.stdout, this.stderr),
      this.curlFactory.newCurlGolang(this.request, this.stdout, this.stderr)
    );
  } 

  private final String runtime() {
    return "/" + this.runtime.or(DEFAULT_RUNTIME);
  }

  private final String runtimeID() {
    return CurlModule.NAMESPACE + runtime();
  }

}
