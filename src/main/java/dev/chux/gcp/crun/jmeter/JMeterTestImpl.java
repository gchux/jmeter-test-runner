package dev.chux.gcp.crun.jmeter;

import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.assistedinject.Assisted;
import dev.chux.gcp.crun.process.ProcessProvider;
import dev.chux.gcp.crun.process.ProcessOutput;
import dev.chux.gcp.crun.process.ProcessOutputFactory;

public class JMeterTestImpl implements JMeterTest {

  private final JMeterTestConfig jMeterTestConfig;
  private final Optional<OutputStream> stream;
  private final boolean closeable;
  private final ProcessOutputFactory processOutputFactory;

  private final AtomicBoolean started;

  @AssistedInject
  public JMeterTestImpl(ProcessOutputFactory processOutputFactory, 
      @Assisted JMeterTestConfig jMeterTestConfig) {
    this(processOutputFactory, jMeterTestConfig, Optional.empty(), false);
  }

  @AssistedInject
  public JMeterTestImpl(ProcessOutputFactory processOutputFactory, 
      @Assisted JMeterTestConfig jMeterTestConfig, 
      @Assisted OutputStream stream) {
    this(processOutputFactory, jMeterTestConfig, Optional.ofNullable(stream), false);
  }

  @AssistedInject
  public JMeterTestImpl(ProcessOutputFactory processOutputFactory, 
      @Assisted JMeterTestConfig jMeterTestConfig, 
      @Assisted OutputStream stream, 
      @Assisted boolean closeable) {
    this(processOutputFactory, jMeterTestConfig, Optional.ofNullable(stream), closeable);
  }

  public JMeterTestImpl(ProcessOutputFactory processOutputFactory,
      JMeterTestConfig jMeterTestConfig, Optional<OutputStream> stream, boolean closeable) {
    this.jMeterTestConfig = jMeterTestConfig;
    this.stream = stream;
    this.closeable = closeable;
    this.processOutputFactory = processOutputFactory;
    this.started = new AtomicBoolean(false);
  }

  @Override
  public ProcessBuilder getBuilder() {
    final ProcessBuilder builder = new ProcessBuilder("jmeter", 
        "-n", 
        "-l", "/dev/stdout",
        "-j", "/dev/stdout",
        "-t", "/test.jmx", 
        "-Jhost=" + this.jMeterTestConfig.host(), 
        "-Jpath=" + this.jMeterTestConfig.path(), 
        "-Jconcurrency=" + Integer.toString(this.jMeterTestConfig.concurrency(), 10), 
        "-Jduration=" + Integer.toString(this.jMeterTestConfig.duration(), 10), 
        "-Jrampup_time=" + Integer.toString(this.jMeterTestConfig.rampupTime(), 10), 
        "-Jrampup_steps=" + Integer.toString(this.jMeterTestConfig.rampupSteps(), 10));
    builder.redirectErrorStream(true);
    return builder;
  } 

  @Override
  public ProcessOutput getOutput() {
    if( this.stream.isPresent() ) {
      return this.processOutputFactory.create(this.stream.get(), this.closeable);
    }
    return this.processOutputFactory.create(System.out, /* closeable */ false);
  }

}
