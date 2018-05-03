/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.heron.spi.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;

import org.apache.heron.api.generated.TopologyAPI;
import org.apache.heron.proto.system.PhysicalPlans;
import org.apache.heron.proto.tmaster.TopologyMaster;
import org.apache.heron.spi.statemgr.SchedulerStateManagerAdaptor;

public final class TMasterUtils {
  public enum TMasterCommand {
    ACTIVATE,
    DEACTIVATE,
    RUNTIME_CONFIG_UPDATE
  }

  private static final Logger LOG = Logger.getLogger(TMasterUtils.class.getName());

  private TMasterUtils() {
  }

  /**
   * Communicate with TMaster with command
   *
   * @param command the command requested to TMaster, activate or deactivate.
   */
  @VisibleForTesting
  public static void sendToTMaster(String command,
                                   String topologyName,
                                   SchedulerStateManagerAdaptor stateManager,
                                   NetworkUtils.TunnelConfig tunnelConfig)
      throws TMasterException {
    final List<String> empty = new ArrayList<String>();
    sendToTMasterWithArguments(command, topologyName, empty, stateManager, tunnelConfig);
  }

  @VisibleForTesting
  public static void sendToTMasterWithArguments(String command,
                                                String topologyName,
                                                List<String> arguments,
                                                SchedulerStateManagerAdaptor stateManager,
                                                NetworkUtils.TunnelConfig tunnelConfig)
      throws TMasterException {
    // fetch the TMasterLocation for the topology
    LOG.fine("Fetching TMaster location for topology: " + topologyName);

    TopologyMaster.TMasterLocation location = stateManager.getTMasterLocation(topologyName);
    if (location == null) {
      throw new TMasterException("Failed to fetch TMaster location for topology: "
        + topologyName);
    }

    LOG.fine("Fetched TMaster location for topology: " + topologyName);

    // for the url request to be sent to TMaster
    String url = String.format("http://%s:%d/%s?topologyid=%s",
        location.getHost(), location.getControllerPort(), command, location.getTopologyId());
    // Append extra url arguments
    for (String arg: arguments) {
      url += "&";
      url += arg;
    }

    try {
      URL endpoint = new URL(url);
      LOG.fine("HTTP URL for TMaster: " + endpoint);
      sendGetRequest(endpoint, command, tunnelConfig);
    } catch (MalformedURLException e) {
      throw new TMasterException("Invalid URL for TMaster endpoint: " + url, e);
    }
  }

  private static void sendGetRequest(URL endpoint, String command,
                                     NetworkUtils.TunnelConfig tunnelConfig)
      throws TMasterException {
    // create a URL connection
    HttpURLConnection connection =
        NetworkUtils.getProxiedHttpConnectionIfNeeded(endpoint, tunnelConfig);
    if (connection == null) {
      throw new TMasterException(String.format(
          "Failed to get a HTTP connection to TMaster: %s", endpoint));
    }
    LOG.fine("Successfully opened HTTP connection to TMaster");
    // now sent the http request
    NetworkUtils.sendHttpGetRequest(connection);
    LOG.fine("Sent the HTTP payload to TMaster");

    // get the response and check if it is successful
    try {
      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        LOG.fine("Successfully got a HTTP response from TMaster using command: " + command);
      } else {
        throw new TMasterException(
            String.format("Non OK HTTP response %d from TMaster for command %s",
            responseCode, command));
      }
    } catch (IOException e) {
      throw new TMasterException(String.format(
          "Failed to receive HTTP response from TMaster using command: `%s`", command), e);
    } finally {
      connection.disconnect();
    }
  }

  /**
   * Get current running TopologyState
   */
  private static TopologyAPI.TopologyState getRuntimeTopologyState(
      String topologyName,
      SchedulerStateManagerAdaptor statemgr) throws TMasterException {
    PhysicalPlans.PhysicalPlan plan = statemgr.getPhysicalPlan(topologyName);

    if (plan == null) {
      throw new TMasterException(String.format(
        "Failed to get physical plan for topology '%s'", topologyName));
    }

    return plan.getTopology().getState();
  }

  public static void transitionTopologyState(String topologyName,
                                             TMasterCommand topologyStateControlCommand,
                                             SchedulerStateManagerAdaptor statemgr,
                                             TopologyAPI.TopologyState startState,
                                             TopologyAPI.TopologyState expectedState,
                                             NetworkUtils.TunnelConfig tunnelConfig)
      throws TMasterException {
    TopologyAPI.TopologyState state = TMasterUtils.getRuntimeTopologyState(topologyName, statemgr);
    if (state == null) {
      throw new TMasterException(String.format(
          "Topology '%s' is not initialized yet", topologyName));
    }

    if (state == expectedState) {
      LOG.warning(String.format(
          "Topology %s command received but topology '%s' already in state %s",
          topologyStateControlCommand, topologyName, state));
      return;
    }

    if (state != startState) {
      throw new TMasterException(String.format(
          "Topology '%s' is not in state '%s'", topologyName, startState));
    }

    String command = topologyStateControlCommand.name().toLowerCase();
    TMasterUtils.sendToTMaster(command, topologyName, statemgr, tunnelConfig);

    LOG.log(Level.INFO,
        "Topology command {0} completed successfully.", topologyStateControlCommand);
  }

  public static void sendRuntimeConfig(String topologyName,
                                       TMasterCommand topologyStateControlCommand,
                                       SchedulerStateManagerAdaptor statemgr,
                                       String[] configs,
                                       NetworkUtils.TunnelConfig tunnelConfig)
      throws TMasterException {
    final String runtimeConfigKey = "runtime-config";
    final String runtimeConfigUpdateEndpoint = "runtime_config/update";

    List<String> arguments = new ArrayList<String>();
    for (String config: configs) {
      arguments.add(runtimeConfigKey + "=" + config);
    }

    TMasterUtils.sendToTMasterWithArguments(
        runtimeConfigUpdateEndpoint, topologyName, arguments, statemgr, tunnelConfig);

    LOG.log(Level.INFO,
        "Topology command {0} completed successfully.", topologyStateControlCommand);
  }
}
