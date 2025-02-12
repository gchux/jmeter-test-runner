package dev.chux.gcp.crun.gcloud;

import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.SerializedName;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Strings.emptyToNull;

public class GCloudCommandConfig {

  @Since(1.0)
  @Expose(deserialize=false, serialize=true)
  @SerializedName(value="namespace", alternate={"ns"})
  private String namespace;

  @Since(1.0)
  @Expose(deserialize=true, serialize=true)
  @SerializedName(value="groups", alternate={"g"})
  private List<String> groups;

  @Since(1.0)
  @Expose(deserialize=true, serialize=true)
  @SerializedName(value="command", alternate={"cmd"})
  private String command;

  @Since(1.0)
  @Expose(deserialize=true, serialize=true)
  @SerializedName(value="flags", alternate={"fl"})
  private Map<String, String> flags;

  @Since(1.0)
  @Expose(deserialize=true, serialize=true)
  @SerializedName(value="arguments", alternate={"args"})
  private List<String> arguments;

  @Since(1.0)
  @Expose(deserialize=true, serialize=true)
  @SerializedName(value="format", alternate={"fmt"})
  private String format;

  public GCloudCommandConfig() {}
  
  public String namespace() {
    return this.namespace;
  }

  public Optional<String> optionalNamespace() {
    return fromNullable(emptyToNull(this.namespace));
  }

  public void namespace(final String namespace) {
    this.namespace = namespace;
  }

  public String command() {
    return this.command;
  }

  public Optional<String> optionalCommand() {
    return fromNullable(emptyToNull(this.command));
  }

  public String format() {
    return emptyToNull(this.format);
  }

  public Optional<String> optionalFormat() {
    return fromNullable(this.format());
  }

  public List<String> groups() {
    if( this.groups == null ) {
      return ImmutableList.of();
    } 
    return ImmutableList.copyOf(this.groups);
  }

  public List<String> arguments() {
    if( this.arguments == null ) {
      return ImmutableList.of();
    } 
    return ImmutableList.copyOf(this.arguments);
  }

  public Map<String, String> flags() {
    if( this.flags == null ) {
      return ImmutableMap.of();
    } 
    return ImmutableMap.copyOf(this.flags);
  }

}
