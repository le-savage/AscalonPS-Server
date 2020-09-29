package com.janus.world.entity.impl.player;

import com.janus.GameServer;
import com.janus.GameSettings;
import com.janus.engine.task.TaskManager;
import com.janus.engine.task.impl.*;
import com.janus.model.*;
import com.janus.model.container.impl.Bank;
import com.janus.model.container.impl.Equipment;
import com.janus.model.definitions.NpcDefinition;
import com.janus.model.definitions.WeaponAnimations;
import com.janus.model.definitions.WeaponInterfaces;
import com.janus.net.PlayerSession;
import com.janus.net.SessionState;
import com.janus.net.security.ConnectionHandler;
import com.janus.util.Misc;
import com.janus.world.World;
import com.janus.world.content.*;
import com.janus.world.content.clan.ClanChatManager;
import com.janus.world.content.combat.effect.CombatPoisonEffect;
import com.janus.world.content.combat.effect.CombatTeleblockEffect;
import com.janus.world.content.combat.magic.Autocasting;
import com.janus.world.content.combat.prayer.CurseHandler;
import com.janus.world.content.combat.prayer.PrayerHandler;
import com.janus.world.content.combat.pvp.BountyHunter;
import com.janus.world.content.combat.range.DwarfMultiCannon;
import com.janus.world.content.combat.weapon.CombatSpecial;
import com.janus.world.content.combat.weapon.effects.impl.weapon.ItemEffect;
import com.janus.world.content.minigames.impl.Barrows;
import com.janus.world.content.questtab.QuestTab;
import com.janus.world.content.skill.impl.hunter.Hunter;
import com.janus.world.content.skill.impl.slayer.Slayer;
import com.janus.world.content.skillingtasks.SkillingTasks;

import static com.janus.world.content.ProfileViewing.getAccountWorth;
import static com.janus.world.content.StaffList.getPrefix;


public class PlayerHandler {


    /**
     * Gets the player according to said name.
     *
     * @param name The name of the player to search for.
     * @return The player who has the same name as said param.
     */


    public static Player getPlayerForName(String name) {
        for (Player player : World.getPlayers()) {
            if (player == null)
                continue;
            if (player.getUsername().equalsIgnoreCase(name))
                return player;
        }
        return null;
    }

    public static void handleLogin(Player player) {
        if (player.getSession().getState().equals(SessionState.LOGGED_IN)) {
            return;
        }
        System.out.println("[World] Registering player - [username, host, mac, UID] : [" + player.getUsername() + ", " + player.getHostAddress() + ", " + player.getMac() + ", " + player.getUUID() + "]");
        /*** DISCORD PRESENCE TEST ***/
        player.getPacketSender().sendRichPresenceDetails("IGN: " + player.getUsername() + " | Total Lvl: " + player.getSkillManager().getTotalLevel());
        player.getPacketSender().sendRichPresenceBigPictureText("Net Worth: " + Misc.setupMoney(getAccountWorth(player)));
        player.getPacketSender().sendRichPresenceState("Exploring Janus..");
        player.getPacketSender().sendRichPresenceSmallPictureText("Combat Lvl: " + player.getSkillManager().getCombatLevel());
        player.getPacketSender().sendSmallImageKey("home");


        player.getPlayerOwnedShopManager().hookShop();
        player.setActive(true);
        ConnectionHandler.add(player.getHostAddress());
        World.getPlayers().add(player);
        World.updatePlayersOnline();
        PlayersOnlineInterface.add(player);

        SkillingTasks.loadData();
        if (SkillingTasks.currentTask == null && player.getSkillTaskOrdinal() > 0) {
            SkillingTasks.restoreTaskOnLogin(player);
        }

        if (player.getSession().getState().equals(SessionState.LOGGED_IN)) {
            player.logout();
        } else {
            player.getSession().setState(SessionState.LOGGED_IN);
        }

        player.getPacketSender().sendMapRegion().sendDetails();

        player.getRecordedLogin().reset();

        player.getPacketSender().sendTabs();

        for (int i = 0; i < player.getBanks().length; i++) {
            if (player.getBank(i) == null) {
                player.setBank(i, new Bank(player));
            }
        }
        player.getInventory().refreshItems();
        player.getEquipment().refreshItems();

        WeaponAnimations.assign(player, player.getEquipment().get(Equipment.WEAPON_SLOT));
        WeaponInterfaces.assign(player, player.getEquipment().get(Equipment.WEAPON_SLOT));
        CombatSpecial.updateBar(player);
        BonusManager.update(player);
        QuestTab.refreshPanel();


        player.getFarming().load();
        Slayer.checkDuoSlayer(player, true);
        for (Skill skill : Skill.values()) {
            player.getSkillManager().updateSkill(skill);
        }

        player.getRelations().setPrivateMessageId(1).onLogin(player).updateLists(true);

        player.getPacketSender().sendConfig(172, player.isAutoRetaliate() ? 1 : 0)
                .sendTotalXp(player.getSkillManager().getTotalGainedExp())
                .sendConfig(player.getFightType().getParentId(), player.getFightType().getChildId())
                .sendRunStatus()
                .sendRunEnergy(player.getRunEnergy())
                .sendString(8135, "" + player.getMoneyInPouch())
                .sendInteractionOption("Follow", 3, false)
                .sendInteractionOption("Trade With", 4, false)
                .sendInterfaceRemoval().sendString(39161, "@or2@Server time: @or2@[ @yel@" + Misc.getCurrentServerTime() + "@or2@ ]");

        Autocasting.onLogin(player);
        PrayerHandler.deactivateAll(player);
        CurseHandler.deactivateAll(player);
        BonusManager.sendCurseBonuses(player);
        Achievements.updateInterface(player);
        Barrows.updateInterface(player);

        TaskManager.submit(new PlayerSkillsTask(player));
        if (player.isPoisoned()) {
            TaskManager.submit(new CombatPoisonEffect(player));
        }
        if (player.getPrayerRenewalPotionTimer() > 0) {
            TaskManager.submit(new PrayerRenewalPotionTask(player));
        }
        if (player.getOverloadPotionTimer() > 0) {
            TaskManager.submit(new OverloadPotionTask(player));
        }
        if (player.getTeleblockTimer() > 0) {
            TaskManager.submit(new CombatTeleblockEffect(player));
        }
        if (player.getSkullTimer() > 0) {
            player.setSkullIcon(1);
            TaskManager.submit(new CombatSkullEffect(player));
        }
        if (player.getFireImmunity() > 0) {
            FireImmunityTask.makeImmune(player, player.getFireImmunity(), player.getFireDamageModifier());
        }
        if (player.getSpecialPercentage() < 100) {
            TaskManager.submit(new PlayerSpecialAmountTask(player));
        }
        if (player.hasStaffOfLightEffect()) {
            TaskManager.submit(new StaffOfLightSpecialAttackTask(player));
        }
        if (player.getMinutesBonusExp() >= 0) {
            TaskManager.submit(new BonusExperienceTask(player));
        }

        player.getUpdateFlag().flag(Flag.APPEARANCE);

        Locations.login(player);

        player.getPacketSender().sendMessage("@blu@Welcome to Janus! We hope you enjoy your stay!");


        if (player.experienceLocked()) {
            player.getPacketSender().sendMessage("@red@Warning: your experience is currently locked.");
        }
        //ClanChatManager.handleLogin(player);
        ClanChatManager.join(player, "Help");

        if (Misc.isWeekend()) {
            player.getPacketSender().sendMessage("<img=10> <col=008FB2>Janus currently has a bonus experience event going on, make sure to use it!");
        }
        if (GameSettings.WEEKLY_BOSS_ENABLED && GameSettings.CURRENT_BOSS > 1) {
            player.getPacketSender().sendMessage("<img=10> @blu@This weeks weekly boss is " + NpcDefinition.forId(GameSettings.CURRENT_BOSS)+"!");
        }
        if (WellOfGoodwill.isActive()) {
            player.getPacketSender().sendMessage("<img=10> @blu@The Fountain of Goodwill is granting 30% Bonus xp for another " + WellOfGoodwill.getMinutesRemaining() + " minutes.");
        }
        PlayerPanel.refreshPanel(player);

        //New player
        if (player.newPlayer()) {
            StartScreen.open(player);
            player.setPlayerLocked(true);
        }

        if (!player.hasUsedBossTierTP()){
            player.getPacketSender().sendMessage("@red@Try out our brand new minigame at ::boss - Insane loot up for grabs!");
        }

        if (!player.hasPlayedNewBarrows()) {
            player.getPacketSender().sendMessage("@red@Check out the new version of barrows! It's 100x more enjoyable <3");
        }

        player.getPacketSender().updateSpecialAttackOrb().sendIronmanMode(player.getGameMode().ordinal());

        if (player.getRights().isStaff() && !player.getRights().equals(PlayerRights.OWNER)) {
            World.sendFilteredMessage("<img=" + player.getRights().ordinal() + "><col=6600CC> " + Misc.formatText(player.getRights().toString().toLowerCase()) + " " + player.getUsername() + " has just logged in, feel free to message them for support.");
        }

        if (player.getRights().isStaff()) {
            if (!StaffList.staff.contains(getPrefix(player) + " @gre@" + player.getUsername())) {
                StaffList.login(player);
            }
        }

        PlayerPanel.refreshPanel(player);
        ItemEffect.refreshEffects(player);

        player.getKillsTracker().forEach(entry -> {
            if (entry.npcId <= 0) {
                entry.npcId = NpcDefinition.forName(entry.npcName).getId();
            }
        });

        if (player.getPointsHandler().getAchievementPoints() == 0) {
            Achievements.setPoints(player);
        }

        if (player.getPlayerOwnedShopManager().getEarnings() > 0) {
            player.sendMessage("<col=FF0000>You have unclaimed earnings in your player owned shop!");
        }

        player.getSummoning().login();

        if (!player.newPlayer()) {
            if (!PlayerPunishment.hasClaimedDailyRewardIP(player.getHostAddress()) || !PlayerPunishment.hasClaimedDailyRewardUID(player.getUUID()) || !PlayerPunishment.hasClaimedDailyRewardMAC(player.getMac())) {
                if (System.currentTimeMillis() >= player.getDailyReward().getNextRewardTime())
                    player.getDailyReward().openInterface();//DAILY REWARD
            }
        }

        if ((Misc.getMinutesPlayed(player) > 5) && player.getDifficulty() == Difficulty.Default) {
            player.getPacketSender().sendInterface(3200);
        }

        if ((player.getDifficulty() == Difficulty.Default) && (!player.newPlayer())) {
            player.getPacketSender().sendMessage("@red@ Select a difficulty using the selector to start gaining XP!");
        }

        PlayerLogs.log(player.getUsername(), "Login from IP: " + player.getHostAddress() + ", Mac: " + player.getMac() + ", UUID: " + player.getUUID());
    }

    public static boolean handleLogout(Player player) {
        try {

            PlayerSession session = player.getSession();

            if (session.getChannel().isOpen()) {
                session.getChannel().close();
            }

            if (!player.isRegistered()) {
                return true;
            }

            boolean exception = GameServer.isUpdating() || World.getLogoutQueue().contains(player) && player.getLogoutTimer().elapsed(600000);
            if (player.logout() || exception) {
                //new Thread(new HighscoresHandler(player)).start();
                System.out.println("[World] Deregistering player - [username, host, mac, UID] : [" + player.getUsername() + ", " + player.getHostAddress() + ", " + player.getMac() + ", " + player.getUUID() + "]");
                player.getSession().setState(SessionState.LOGGING_OUT);
                ConnectionHandler.remove(player.getHostAddress());
                player.setTotalPlayTime(player.getTotalPlayTime() + player.getRecordedLogin().elapsed());
                player.getPacketSender().sendInterfaceRemoval();
                if (player.getCannon() != null) {
                    DwarfMultiCannon.pickupCannon(player, player.getCannon(), true);
                }
                if (exception && player.getResetPosition() != null) {
                    player.moveTo(player.getResetPosition());
                    player.setResetPosition(null);
                }
                if (player.getRegionInstance() != null) {
                    player.getRegionInstance().destruct();
                }


                if (player.getRights().isStaff()) {
                    StaffList.logout(player);
                }
                Hunter.handleLogout(player);
                Locations.logout(player);
                player.getSummoning().unsummon(false, false);
                player.getFarming().save();
                player.getPlayerOwnedShopManager().unhookShop();
                BountyHunter.handleLogout(player);
                ClanChatManager.leave(player, false);
                player.getRelations().updateLists(false);
                PlayersOnlineInterface.remove(player);
                TaskManager.cancelTasks(player.getCombatBuilder());
                TaskManager.cancelTasks(player);
                player.save();
                World.getPlayers().remove(player);
                session.setState(SessionState.LOGGED_OUT);
                World.updatePlayersOnline();
                //PlayerLogs.readTradeLog(player.getUsername().toUpperCase());
                /* PlayerLogs.eraseFile(player.getUsername().toUpperCase());*/
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
}
