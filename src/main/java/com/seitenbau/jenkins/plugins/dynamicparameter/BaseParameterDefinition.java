/*
 * Copyright 2012 Seitenbau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.seitenbau.jenkins.plugins.dynamicparameter;

import hudson.EnvVars;
import hudson.Util;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.Label;
import hudson.model.StringParameterValue;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.util.JSONUtils;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;

import com.seitenbau.jenkins.plugins.dynamicparameter.scriptler.ScriptlerParameterDefinition;
import com.seitenbau.jenkins.plugins.dynamicparameter.util.JenkinsUtils;

/**
 * Base class for all script parameter definition classes.
 */
public abstract class BaseParameterDefinition extends SimpleParameterDefinition
{
  /** Serial version UID. */
  private static final long serialVersionUID = -4415132917610378545L;

  /** Logger. */
  protected static final Logger logger = Logger.getLogger(ScriptlerParameterDefinition.class
      .getName());

  /** UUID identifying the current parameter. */
  private final UUID _uuid;

  /** Flag showing if the script should be executed remotely. */
  private final Boolean _remote;

  /**
   * Constructor.
   * @param name parameter name
   * @param description parameter description
   * @param uuid UUID of the parameter definition
   * @param remote flag showing if the script should be executed remotely
   */
  protected BaseParameterDefinition(String name, String description, String uuid, Boolean remote)
  {
    super(name, description);
    _remote = remote;

    if (StringUtils.length(uuid) == 0)
    {
      _uuid = UUID.randomUUID();
    }
    else
    {
      _uuid = UUID.fromString(uuid);
    }
  }

  /**
   * Return a Parameter value object for a command line parameter.
   */
  @Override
  public ParameterValue createValue(String value)
  {
    // Fix for issue https://github.com/Seitenbau/sb-jenkins-dynamicparameter/issues/3
    StringParameterValue parameterValue = createStringParameterValueFor(this.getName(), value);
    return checkParameterValue(parameterValue);
  }

  /**
   * Should the script be executed to on a remote slave?
   * @return {@code true} if the script should be executed remotely
   */
  public final boolean isRemote()
  {
    if(_remote == null) {
      return true;
    }
    return _remote;
  }

  /**
   * Get unique id for this parameter definition.
   * @return the _uuid
   */
  public final UUID getUUID()
  {
    return _uuid;
  }

  /**
   * Get the script result as a list.
   * @return list of values if the script returns a non-null list;
   *         {@link Collections#EMPTY_LIST}, otherwise
   */
  @SuppressWarnings("unchecked")
  public final List<Object> getScriptResultAsList(Map<String, String> parameters)
  {
    Object value = executeScript(parameters);
    if (value instanceof List)
    {
      return (List<Object>) value;
    }
    String name = getName();
    String msg = String.format("Script parameter with name '%s' the value is not a instance of java.util.List the parameter value is : %s", name, value);
    logger.info(msg);
    return Collections.EMPTY_LIST;
  }

  /**
   * Get the script result as a string.
   * @return the default value generated by the script or {@code null}
   */
  public final String getScriptResultAsString(Map<String, String> parameters)
  {
    Object value = executeScript(parameters);
    return ObjectUtils.toString(value, null);
  }

  @Override
  public final ParameterValue createValue(StaplerRequest req, JSONObject jo)
  {
    final JSONObject parameterJsonModel = new JSONObject(false);
    final Object value = jo.get("value");
    final String valueAsText;
    if (JSONUtils.isArray(value)) 
    {
      valueAsText = ((JSONArray)value).join(",", true);
    } else 
    {
      valueAsText = String.valueOf(value);
    }
    parameterJsonModel.put("name",  jo.get("name"));
    parameterJsonModel.put("value", valueAsText);

    StringParameterValue parameterValue = req.bindJSON(StringParameterValue.class, parameterJsonModel);
    parameterValue.setDescription(getDescription());
    return checkParameterValue(parameterValue);
  }

  /**
   * Factory methods creates a String parameter value object for the given value.
   * @param value of the object
   * @return String parameter value object not null.
   */
  private StringParameterValue createStringParameterValueFor(String name, String value)
  {
    String description = getDescription();
    StringParameterValue parameterValue = new StringParameterValue(name, value, description);
    return parameterValue;
  }

  /**
   * Checks the validity of the given parameter value. The default implementation does nothing and
   * should be overridden.
   * @param value parameter value to check
   * @return if the value is valid the same parameter value
   * @throws IllegalArgumentException if the value in not valid
   */
  protected StringParameterValue checkParameterValue(StringParameterValue value)
  {
    return value;
  }

  /**
   * Prepare a local call.
   * @return call instance
   * @throws Exception if a call instance cannot be created
   */
  protected abstract Callable<Object, Throwable> prepareLocalCall(Map<String, String> parameters) throws Exception;

  /**
   * Prepare a remote call.
   * @param channel channel to the remote slave where the call will be executed
   * @return call instance
   * @throws Exception if a call instance cannot be created
   */
  protected abstract Callable<Object, Throwable> prepareRemoteCall(VirtualChannel channel, Map<String, String> parameters)
      throws Exception;

  /**
   * Execute the current script either remotely or locally, depending on the remote flag.
   * @return script result
   */
  private final Object executeScript(Map<String, String> parameters)
  {
    // It contains the env vars of the current JVM process, not necessarily the master/slave vars
	final Map<String, String> envVars = EnvVars.masterEnvVars;
	for (Map.Entry<String,String> entry: parameters.entrySet()) {
      entry.setValue(Util.replaceMacro(entry.getValue(), envVars));
    }
    try
    {
      if (isRemote())
      {
        Label label = JenkinsUtils.findProjectLabel(getUUID());
        if (label == null)
        {
          logger.warning(String.format(
            "No label is assigned to project; script for parameter '%s' will be executed on master",
            getName()));
        }
        else
        {
          VirtualChannel channel = JenkinsUtils.findActiveChannel(label);
          if (channel == null)
          {
            logger.warning(String.format(
                "Cannot find an active node of the label '%s' where to execute the script",
                label.getDisplayName()));
          }
          else
          {
            Callable<Object, Throwable> call = prepareRemoteCall(channel, parameters);
            return channel.call(call);
          }
        }
      }
      for (Map.Entry<String,String> entry: parameters.entrySet()) {
        entry.setValue(Util.replaceMacro(entry.getValue(), envVars));
      }
      Callable<Object, Throwable> call = prepareLocalCall(parameters);
      return call.call();
    }
    catch (Throwable e)
    {
      String msg = String.format("Error during executing script for parameter '%s'", getName());
      logger.log(Level.SEVERE, msg, e);
    }
    return null;
  }

}
