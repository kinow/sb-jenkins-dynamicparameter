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

import hudson.Extension;
import hudson.model.StringParameterValue;

import java.util.List;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;

/** Choice parameter, with dynamically generated list of values. */
public class ChoiceParameterDefinition extends ScriptParameterDefinition
{
  /** Serial version UID. */
  private static final long serialVersionUID = 5454277528808586236L;

  /**
   * Constructor.
   * @param name parameter name
   * @param script script, which generates the parameter value
   * @param description parameter description
   * @param uuid identifier (optional)
   * @param remote execute the script on a remote node
   */
  @DataBoundConstructor
  public ChoiceParameterDefinition(String name, String script, String description, String uuid,
      boolean remote, String classPath)
  {
    super(name, script, description, uuid, remote, classPath);
  }

  /**
   * Get the possible choice, generated by the script.
   * @return list of values if the script returns a non-null list;
   *         {@link Collections#EMPTY_LIST}, otherwise
   */
  public final List<Object> getChoices()
  {
    return getScriptResultAsList();
  }

  /**
   * Check if the given parameter value is within the list of possible
   * values.
   * @param parameter parameter value to check
   * @return the value if it is valid
   */
  @Override
  protected StringParameterValue checkParameterValue(StringParameterValue parameter)
  {
    String actualValue = ObjectUtils.toString(parameter.value);
    for (Object choice : getChoices())
    {
      String choiceValue = ObjectUtils.toString(choice);
      if (StringUtils.equals(actualValue, choiceValue))
      {
        return parameter;
      }
    }
    throw new IllegalArgumentException("Illegal choice: " + actualValue);
  }

  /** Parameter descriptor. */
  @Extension
  public static final class DescriptorImpl extends BaseDescriptor
  {
    private static final String DISPLAY_NAME = "DisplayName";

    @Override
    public final String getDisplayName()
    {
      return ResourceBundleHolder.get(ChoiceParameterDefinition.class).format(DISPLAY_NAME);
    }
  }
}
