package dev.chux.gcp.crun.gcloud;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import com.google.common.base.Supplier;
import com.google.common.base.Optional;

public class GCloudFormatSupplier implements Supplier<String> {

  private static final String DEFAULT_GCLOUD_FORMAT = "json";

  @Inject(optional=true)
  @Named("env.GCLOUD_FORMAT")
  String gcloudFormatEnv = null;

  @Inject(optional=true)
  @Named("gcloud.format")
  String gcloudFormatProp = null;

  public String get() {
    return optionalEnv().or(optionalProp()).or(DEFAULT_GCLOUD_FORMAT);
  }

  private final Optional<String> optional(final String value) {
    return Optional.fromNullable(value);
  }

  private final Optional<String> optionalEnv() {
    return optional(this.gcloudFormatEnv);
  }

  private final Optional<String> optionalProp() {
    return optional(this.gcloudFormatProp);
  }

}
