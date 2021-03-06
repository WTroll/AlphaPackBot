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

import com.vb.alphapackbot.Cache;
import com.vb.alphapackbot.Commands;
import com.vb.alphapackbot.RarityTypes;
import com.vb.alphapackbot.TypingManager;
import com.vb.alphapackbot.UserData;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;

public class CountCommand extends AbstractCommand {
  private static final Logger log = Logger.getLogger(CountCommand.class);

  public CountCommand(final GuildMessageReceivedEvent event,
                      final Commands command,
                      final Cache cache,
                      final TypingManager typingManager) {
    super(event, command, cache, typingManager);
  }

  @Override
  public void run() {
    String authorId = event.getAuthor().getId();
    UserData userData = getRaritiesForUser(messages, authorId);
    printRarityPerUser(userData, event.getChannel());
    finish();
  }

  /**
   * Obtains all rarity data for specific user.
   * Check {@link CountCommand#computeRarity(BufferedImage)}
   *
   * @param messages Messages from which rarities will be extracted
   * @param authorId ID of request message author
   * @return returns {@link UserData} containing count of all rarities from user.
   */
  public UserData getRaritiesForUser(@NotNull List<Message> messages,
                                     @NotNull String authorId) {
    System.out.println("Getting rarity per user...");
    UserData userData = new UserData(authorId);
    for (Message message : messages) {
      try {
        RarityTypes rarity = loadOrComputeRarity(message);
        userData.increment(rarity);
      } catch (IOException e) {
        log.error("Exception getting image!", e);
      }
    }
    return userData;
  }

  /**
   * Prints user data to console and sends message to channel if enabled.
   *
   * @param userData Data to be printed
   * @param channel  Channel to print data to
   */
  public void printRarityPerUser(@NotNull UserData userData, @NotNull TextChannel channel) {
    if (!properties.isPrintingEnabled()) {
      return;
    }
    int total = userData.getRarityData().get(RarityTypes.COMMON)
        + userData.getRarityData().get(RarityTypes.UNCOMMON)
        + userData.getRarityData().get(RarityTypes.RARE)
        + userData.getRarityData().get(RarityTypes.EPIC)
        + userData.getRarityData().get(RarityTypes.LEGENDARY)
        + userData.getRarityData().get(RarityTypes.UNKNOWN);

    String message = "<@" + userData.getAuthorId() + ">\n"
        + "Total: " + total + " \n"
        + RarityTypes.COMMON + ": " + userData.getRarityData().get(RarityTypes.COMMON) + "\n"
        + RarityTypes.UNCOMMON + ": " + userData.getRarityData().get(RarityTypes.UNCOMMON) + "\n"
        + RarityTypes.RARE + ": " + userData.getRarityData().get(RarityTypes.RARE) + "\n"
        + RarityTypes.EPIC + ": " + userData.getRarityData().get(RarityTypes.EPIC) + "\n"
        + RarityTypes.LEGENDARY + ": " + userData.getRarityData().get(RarityTypes.LEGENDARY) + "\n"
        + RarityTypes.UNKNOWN + ": " + userData.getRarityData().get(RarityTypes.UNKNOWN);

    channel.sendMessage(message).complete();
  }
}
