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

package com.vb.alphapackbot.commands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.google.mu.util.concurrent.Retryer;
import com.vb.alphapackbot.Cache;
import com.vb.alphapackbot.Commands;
import com.vb.alphapackbot.Properties;
import com.vb.alphapackbot.RarityTypes;
import com.vb.alphapackbot.TypingManager;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all Commands.
 */
public abstract class AbstractCommand implements Runnable {
  private static final Logger log = Logger.getLogger(AbstractCommand.class);
  private static final int MAX_RETRIEVE_SIZE = 100;
  protected static final Properties properties = Properties.getInstance();
  final List<Message> messages;
  final GuildMessageReceivedEvent event;
  final Commands command;
  final Cache cache;
  final TypingManager typingManager;

  AbstractCommand(final GuildMessageReceivedEvent event,
                  final Commands command,
                  final Cache cache,
                  final TypingManager typingManager) {
    this.messages = getMessages(event.getChannel())
        .stream()
        .filter(x -> !x.getAttachments().isEmpty())
        .filter(x -> x.getAuthor().getId().equals(event.getAuthor().getId()))
        .filter(m -> !m.getContentRaw().contains("*ignored"))
        .collect(Collectors.toList());
    this.event = event;
    this.command = command;
    this.cache = cache;
    this.typingManager = typingManager;
    typingManager.startIfNotRunning(event.getChannel());
  }

  /**
   * Returns all messages from specific channel.
   *
   * @param channel channel to get messages from
   * @return ArrayList of messages
   */
  private @NotNull ArrayList<Message> getMessages(@NotNull TextChannel channel) {
    ArrayList<Message> messages = new ArrayList<>();
    MessageHistory history = channel.getHistory();
    int amount = Integer.MAX_VALUE;

    while (amount > 0) {
      int numToRetrieve = Math.min(amount, MAX_RETRIEVE_SIZE);

      try {
        List<Message> retrieved = new Retryer()
            .upon(RateLimitedException.class, Retryer.Delay.ofMillis(5000).exponentialBackoff(1, 5))
            .retryBlockingly(() -> history.retrievePast(numToRetrieve).complete(true));
        messages.addAll(retrieved);
        if (retrieved.isEmpty()) {
          break;
        }
      } catch (RateLimitedException rateLimitedException) {
        log.warn("Too many requests, waiting 5 seconds.");
      }
      amount -= numToRetrieve;
    }
    return messages;
  }

  public void finish() {
    typingManager.cancelThread(event.getChannel());
    properties.getProcessingCounter().decrement();
  }

  /**
   * Attempts to load rarity from cache, if unsuccessful, computes the rarity from URL.
   *
   * @param message message containing the URL of image.
   * @return rarity extracted from image or loaded from cache.
   * @throws IOException if an I/O exception occurs.
   */
  public RarityTypes loadOrComputeRarity(Message message) throws IOException {
    String messageUrl = message.getAttachments().get(0).getUrl();
    RarityTypes rarity = null;
    if (!message.getContentRaw().isEmpty() && message.getContentRaw().startsWith("*")) {
      Optional<RarityTypes> forcedRarity = RarityTypes.parse(message.getContentRaw().substring(1));
      if (forcedRarity.isPresent()) {
        rarity = forcedRarity.get();
      }
    }
    if (rarity == null) {
      Optional<RarityTypes> cachedValue = cache.getAndParse(messageUrl);
      rarity = cachedValue.orElse(RarityTypes.UNKNOWN);
      if (cachedValue.isEmpty()) {
        rarity = computeRarity(loadImageFromUrl(messageUrl));
        cache.save(messageUrl, rarity.toString());
      }
    }
    if (rarity == RarityTypes.UNKNOWN) {
      log.infof("Unknown rarity in %s!", messageUrl);
    }
    return rarity;
  }

  /**
   * Obtains RarityType value from image.
   *
   * @param image image to be processed
   * @return Rarity from {@link RarityTypes}
   */
  @NotNull
  public RarityTypes computeRarity(@NotNull BufferedImage image) {
    int width = (int) (image.getWidth() * 0.489583); //~940 @ FHD
    int height = (int) (image.getHeight() * 0.83333); //~900 @ FHD
    Color color = new Color(image.getRGB(width, height));
    int[] colors = {color.getRed(), color.getGreen(), color.getBlue()};
    for (RarityTypes rarity : RarityTypes.values()) {
      ImmutableList<Range<Integer>> range = rarity.getRange();
      int hitCounter = 0;
      for (int i = 0; i < 3; i++) {
        if (range.get(i).contains(colors[i])) {
          hitCounter += 1;
        }
      }
      if (hitCounter == 3) {
        return rarity;
      }
    }
    log.infof("R: %d G: %d B: %d", colors[0], colors[1], colors[2]);
    return RarityTypes.UNKNOWN;
  }

  /**
   * Loads image from an URL into a BufferedImage.
   *
   * @param imageUrl URL from which to load image
   * @return {@link BufferedImage}
   * @throws IOException if an I/O exception occurs.
   */
  public BufferedImage loadImageFromUrl(@NotNull String imageUrl) throws IOException {
    try (InputStream in = new URL(imageUrl).openStream()) {
      return ImageIO.read(in);
    }
  }
}
