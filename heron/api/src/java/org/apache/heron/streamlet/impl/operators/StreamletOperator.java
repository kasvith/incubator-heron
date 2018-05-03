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


package org.apache.heron.streamlet.impl.operators;

import java.io.Serializable;

import org.apache.heron.api.bolt.BaseRichBolt;
import org.apache.heron.api.state.State;
import org.apache.heron.api.topology.IStatefulComponent;
import org.apache.heron.api.topology.OutputFieldsDeclarer;
import org.apache.heron.api.tuple.Fields;

/**
 * The Bolt interface that other operators of the streamlet packages extend.
 * The only common stuff amongst all of them is the output streams
 */
public abstract class StreamletOperator extends BaseRichBolt
    implements IStatefulComponent<Serializable, Serializable> {
  private static final long serialVersionUID = 8524238140745238942L;
  private static final String OUTPUT_FIELD_NAME = "output";

  @Override
  public void initState(State<Serializable, Serializable> state) { }

  @Override
  public void preSave(String checkpointId) { }

  /**
   * The operators implementing streamlet functionality have some properties.
   * 1. They all output only one stream
   * 2. They should be able to consume each other's output
   * This imply that the output stream should be named same for all of them.
   */
  @Override
  public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
    outputFieldsDeclarer.declare(new Fields(OUTPUT_FIELD_NAME));
  }
}
