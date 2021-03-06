/*
 *    Copyright 2020 Valentín Bolfík
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.vb.alphapackbot;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * Contains available commands.
 */
public enum Commands {
  COUNT("count"),
  LAST("last"),
  FIRST("first"),
  STATUS("status");

  private static final Map<String, Commands> stringValues = Stream.of(values())
      .collect(Collectors.toMap(Commands::toString, x -> x));

  private final String command;

  Commands(final String command) {
    this.command = command;
  }

  public static Optional<Commands> parse(@Nullable String toParse) {
    if (toParse != null) {
      toParse = toParse.toLowerCase(Locale.ROOT);
    }
    return Optional.ofNullable(stringValues.getOrDefault(toParse, null));
  }

  @Override
  public String toString() {
    return command;
  }
}
