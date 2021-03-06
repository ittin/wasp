/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wasp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.ipc.RemoteException;

/**
 * Subclass if the server knows the entityGroup is now on another server. This allows
 * the client to call the new entityGroup server without calling the master.
 */
public class EntityGroupMovedException extends NotServingEntityGroupException {
  private static final Log LOG = LogFactory
      .getLog(EntityGroupMovedException.class);
  private static final long serialVersionUID = -7232903522310558397L;

  private final String hostname;
  private final int port;

  private static final String HOST_FIELD = "hostname=";
  private static final String PORT_FIELD = "port=";

  public EntityGroupMovedException(final String hostname, final int port) {
    super();
    this.hostname = hostname;
    this.port = port;
  }

  public String getHostname() {
    return hostname;
  }

  public int getPort() {
    return port;
  }

  /**
   * For hadoop.ipc internal call. Do NOT use. We have to parse the hostname to
   * recreate the exception. The input is the one generated by
   * {@link #getMessage()}
   */
  public EntityGroupMovedException(String s) {
    int posHostname = s.indexOf(HOST_FIELD) + HOST_FIELD.length();
    int posPort = s.indexOf(PORT_FIELD) + PORT_FIELD.length();

    String tmpHostname = null;
    int tmpPort = -1;
    try {
      tmpHostname = s.substring(posHostname, s.indexOf(' ', posHostname));
      tmpPort = Integer.parseInt(s.substring(posPort, s.indexOf('.', posPort)));
    } catch (Exception ignored) {
      LOG.warn("Can't parse the hostname and the port from this string: " + s
          + ", " + "Continuing");
    }

    hostname = tmpHostname;
    port = tmpPort;
  }

  @Override
  public String getMessage() {
    return "EntityGroup moved to: " + HOST_FIELD + hostname + " " + PORT_FIELD
        + port + ".";
  }

  /**
   * Look for a EntityGroupMovedException in the exception: - hadoop.ipc wrapped
   * exceptions - nested exceptions Returns null if we didn't find the exception
   * or if it was not readable.
   */
  public static EntityGroupMovedException find(Object exception) {
    if (exception == null || !(exception instanceof Throwable)) {
      return null;
    }

    Throwable cur = (Throwable) exception;
    EntityGroupMovedException res = null;

    while (res == null && cur != null) {
      if (cur instanceof EntityGroupMovedException) {
        res = (EntityGroupMovedException) cur;
      } else {
        if (cur instanceof RemoteException) {
          RemoteException re = (RemoteException) cur;
          Exception e = re
              .unwrapRemoteException(EntityGroupMovedException.class);
          if (e == null) {
            e = re.unwrapRemoteException();
          }
          // unwrapRemoteException can return the exception given as a parameter
          // when it cannot
          // unwrap it. In this case, there is no need to look further
          // noinspection ObjectEquality
          if (e != re) {
            res = find(e);
          }
        }
        cur = cur.getCause();
      }
    }

    if (res != null && (res.getPort() < 0 || res.getHostname() == null)) {
      // We failed to parse the exception. Let's act as we don't find the
      // exception.
      return null;
    } else {
      return res;
    }
  }
}
