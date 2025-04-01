package dev.chux.gcp.crun.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.SerializedName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RestResponse<T> {

  private static final Logger logger = LoggerFactory.getLogger(RestResponse.class);

  @Since(1.0)
  @Expose(deserialize=false, serialize=true)
  @SerializedName(value="data")
  private final T data;

  @Since(1.0)
  @Expose(deserialize=false, serialize=true)
  @SerializedName(value="links")
  private final Map<String, String> links;

  protected RestResponse(final T data) {
    this.data = data;
    this.links = links(data);
  }

  protected abstract Map<
    String, String
  > links(final T data);

}
