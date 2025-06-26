/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.checks.chat;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.chat.analysis.MessageLetterCount;
import fr.neatmonster.nocheatplus.checks.chat.analysis.WordLetterCount;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.LetterEngine;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.feature.INotifyReload;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.ColorUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * Some alternative more or less advanced analysis methods.
 * @author mc_dev
 *
 */
public class Text extends Check implements INotifyReload {

    private LetterEngine engine = null;

    /** Not really cancelled but above threshold for actions. */
    private String lastCancelledMessage = "";
    private long lastCancelledTime = 0;

    private String lastGlobalMessage = "";
    private long lastGlobalTime = 0;

    /**
     * Lock used for updates of global chat state.
     * Protects {@link #lastGlobalMessage}, {@link #lastGlobalTime},
     * {@link #lastCancelledMessage} and {@link #lastCancelledTime}.
     */
    private final Object globalLock = new Object();

    /**
     * Dampening factor for uppercase ratio to prevent over-penalization.
     */
    private static final float UPPERCASE_WEIGHT_FACTOR = 0.6f;

    public Text() {
        super(CheckType.CHAT_TEXT);
        init();
    }

    /**
     * Start analysis.
     * @param player
     *   		The player who issued the message.
     * @param message
     * 			The message to check.
     * @param captcha 
     * 			Used for starting captcha on failure, if configured so.
     * @param alreadyCancelled 
     * @return
     */
    public boolean check(final Player player, final String message, 
            final ChatConfig cc, final IPlayerData pData,
            final ICaptcha captcha, boolean isMainThread, final boolean alreadyCancelled) {
        final ChatData data = pData.getGenericInstance(ChatData.class);

        // Synchronization is handled within {@link #unsafeCheck} to keep the
        // expensive analysis outside of locked sections.
        return unsafeCheck(player, message, captcha, cc, data, pData, isMainThread, alreadyCancelled);
    }

    private void init() {
        // Set some things from the global config.
        final ConfigFile config = ConfigManager.getConfigFile();
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        if (engine != null) {
            engine.clear();
            api.removeComponent(engine);
        }
        engine = new LetterEngine(config);
        api.addComponent(engine);
    }

    @Override
    public void onReload() {
        synchronized(engine) {
            engine.clear();
        }
        init();
    }

    /**
     * Check without further synchronization.
     * @param player
     * @param message
     * @param captcha
     * @param cc
     * @param data
     * @param isMainThread 
     * @param alreadyCancelled 
     * @return
     */
    private boolean unsafeCheck(final Player player, final String message, final ICaptcha captcha,
            final ChatConfig cc, final ChatData data, final IPlayerData pData,
            boolean isMainThread, final boolean alreadyCancelled) {

        synchronized (data) {
            if (captcha.shouldCheckCaptcha(player, cc, data, pData)) {
                captcha.checkCaptcha(player, message, cc, data, isMainThread);
                return true;
            }
        }

        final long time = System.currentTimeMillis();
        final String lcMessage = message.trim().toLowerCase();

        final String lastMessage;
        final long lastTime;
        synchronized (data) {
            data.chatFrequency.update(time);
            lastMessage = data.chatLastMessage;
            lastTime = data.chatLastTime;
        }
        final String lastGlMessage;
        final long lastGlTime;
        final String lastCancMessage;
        final long lastCancTime;
        synchronized (globalLock) {
            lastGlMessage = lastGlobalMessage;
            lastGlTime = lastGlobalTime;
            lastCancMessage = lastCancelledMessage;
            lastCancTime = lastCancelledTime;
        }

        final boolean debug = pData.isDebugActive(type);
        final List<String> debugParts = debug ? new LinkedList<String>() : null;
        if (debug) {
            debugParts.add("Message (length=" + message.length()+"): ");
        }

        final ScoreResult scoreResult = calculateScore(message, lcMessage, time, cc, pData, debug, debugParts,
                lastMessage, lastTime, lastGlMessage, lastGlTime, lastCancMessage, lastCancTime);
        float score = scoreResult.score;
        final MessageLetterCount letterCounts = scoreResult.letterCounts;

        if (debug && score > 0f) {
            debugParts.add("Simple score: " + StringUtil.fdec3.format(score));
        }

        EngineResult engineResult = invokeEngine(letterCounts, player, cc, data);
        float wEngine = engineResult.weight;
        final Map<String, Float> engMap = engineResult.engMap;
        score += wEngine;

        final EvalResult evalResult;
        synchronized (data) {
            evalResult = evaluateFrequencyAndViolations(player, captcha, cc, data, pData, lcMessage, time, score);
            data.chatLastMessage = lcMessage;
            data.chatLastTime = time;
        }
        synchronized (globalLock) {
            lastGlobalMessage = lcMessage;
            lastGlobalTime = time;
        }
        boolean cancel = evalResult.cancel;
        float accumulated = evalResult.accumulated;
        float shortTermAccumulated = evalResult.shortTermAccumulated;

        if (debug) {
            final List<String> keys = new LinkedList<String>(engMap.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                Float s = engMap.get(key);
                if (s.floatValue() > 0.0f)
                    debugParts.add(key + ":" + StringUtil.fdec3.format(s));
            }
            if (wEngine > 0.0f)
                debugParts.add("Engine score (" + (cc.textEngineMaximum?"max":"sum") + "): " + StringUtil.fdec3.format(wEngine));

            debugParts.add("Final score: " +  StringUtil.fdec3.format(score));
            debugParts.add("Normal: min=" +  StringUtil.fdec3.format(cc.textFreqNormMin) +", weight=" +  StringUtil.fdec3.format(cc.textFreqNormWeight) + " => accumulated=" + StringUtil.fdec3.format(accumulated));
            debugParts.add("Short-term: min=" +  StringUtil.fdec3.format(cc.textFreqShortTermMin) +", weight=" +  StringUtil.fdec3.format(cc.textFreqShortTermWeight) + " => accumulated=" + StringUtil.fdec3.format(shortTermAccumulated));
            debugParts.add("vl: " + StringUtil.fdec3.format(data.textVL));
            debug(player, StringUtil.join(debugParts, " | "));
            debugParts.clear();
        }

        return cancel;
    }

    private boolean handleCaptcha(final Player player, final String message, final ICaptcha captcha,
            final ChatConfig cc, final ChatData data, final IPlayerData pData,
            boolean isMainThread, final boolean alreadyCancelled) {
        if (captcha.shouldCheckCaptcha(player, cc, data, pData)) {
            captcha.checkCaptcha(player, message, cc, data, isMainThread);
            return true;
        }
        return alreadyCancelled;
    }

    private static final class ScoreResult {
        final float score;
        final MessageLetterCount letterCounts;
        ScoreResult(float score, MessageLetterCount letterCounts) {
            this.score = score;
            this.letterCounts = letterCounts;
        }
    }

    private ScoreResult calculateScore(final String message, final String lcMessage, final long time,
            final ChatConfig cc, final IPlayerData pData, final boolean debug, final List<String> debugParts,
            final String lastMessage, final long lastTime, final String lastGlobalMessage, final long lastGlobalTime,
            final String lastCancelledMessage, final long lastCancelledTime) {
        final MessageLetterCount letterCounts = new MessageLetterCount(message);
        final int msgLen = message.length();

        final CombinedData cData = pData != null ? pData.getGenericInstance(CombinedData.class) : null;

        float score = 0f;
        score += computeCaseScore(letterCounts, msgLen, cc);
        score += computeRepetitionScore(letterCounts, msgLen, cc);
        score += computeTimeBasedScore(lcMessage, time, cc, lastMessage, lastTime,
                lastGlobalMessage, lastGlobalTime, lastCancelledMessage, lastCancelledTime, cData);
        score += computeWordScore(letterCounts, msgLen, cc);

        return new ScoreResult(score, letterCounts);
    }

    private float computeCaseScore(final MessageLetterCount letterCounts, final int msgLen, final ChatConfig cc) {
        float score = 0f;
        if (letterCounts.fullCount.upperCase > msgLen / 3) {
            final float wUpperCase = UPPERCASE_WEIGHT_FACTOR * letterCounts.fullCount.getUpperCaseRatio();
            score += wUpperCase * cc.textMessageUpperCase;
        }
        return score;
    }

    private float computeRepetitionScore(final MessageLetterCount letterCounts, final int msgLen, final ChatConfig cc) {
        float score = 0f;
        if (msgLen > 4) {
            final float fullRep = letterCounts.fullCount.getLetterCountRatio();
            final float wRepetition = (float) Math.min(msgLen, 128) / 15.0f * Math.abs(0.5f - fullRep);
            score += wRepetition * cc.textMessageLetterCount;

            final float fnWords = (float) letterCounts.words.length / (float) msgLen;
            if (fnWords > 0.75f) {
                score += fnWords * cc.textMessagePartition;
            }
        }
        return score;
    }

    private float computeTimeBasedScore(final String lcMessage, final long time, final ChatConfig cc,
            final String lastMessage, final long lastTime, final String lastGlobalMessage, final long lastGlobalTime,
            final String lastCancelledMessage, final long lastCancelledTime, final CombinedData cData) {
        float score = 0f;
        final long timeout = 8000;

        if (cc.textMsgRepeatSelf != 0f && time - lastTime < timeout
                && StringUtil.isSimilar(lcMessage, lastMessage, 0.8f)) {
            final float timeWeight = (float) (timeout - (time - lastTime)) / (float) timeout;
            score += cc.textMsgRepeatSelf * timeWeight;
        }

        if (cc.textMsgRepeatGlobal != 0f && time - lastGlobalTime < timeout
                && StringUtil.isSimilar(lcMessage, lastGlobalMessage, 0.8f)) {
            final float timeWeight = (float) (timeout - (time - lastGlobalTime)) / (float) timeout;
            score += cc.textMsgRepeatGlobal * timeWeight;
        }

        if (cc.textMsgRepeatCancel != 0f && time - lastCancelledTime < timeout
                && StringUtil.isSimilar(lcMessage, lastCancelledMessage, 0.8f)) {
            final float timeWeight = (float) (timeout - (time - lastCancelledTime)) / (float) timeout;
            score += cc.textMsgRepeatCancel * timeWeight;
        }

        if (cData != null) {
            if (cc.textMsgAfterJoin != 0f && time - cData.lastJoinTime < timeout) {
                final float timeWeight = (float) (timeout - (time - cData.lastJoinTime)) / (float) timeout;
                score += cc.textMsgAfterJoin * timeWeight;
            }
            if (cc.textMsgNoMoving != 0f && time - cData.lastMoveTime > timeout) {
                score += cc.textMsgNoMoving;
            }
        }

        return score;
    }

    private float computeWordScore(final MessageLetterCount letterCounts, final int msgLen, final ChatConfig cc) {
        float wWords = 0.0f;
        final float avwLen = (float) msgLen / (float) letterCounts.words.length;
        for (final WordLetterCount word : letterCounts.words) {
            float wWord = 0.0f;
            final int wLen = word.word.length();
            final float fLenAv = Math.abs(avwLen - (float) wLen) / avwLen;
            wWord += fLenAv * cc.textMessageLengthAv;
            final float fLenMsg = (float) wLen / (float) msgLen;
            wWord += fLenMsg * cc.textMessageLengthMsg;
            float notLetter = word.getNotLetterRatio();
            notLetter *= notLetter;
            wWord += notLetter * cc.textMessageNoLetter;
            wWord *= wWord;
            wWords += wWord;
        }
        wWords /= (float) letterCounts.words.length;
        return wWords;
    }

    private static final class EngineResult {
        final float weight;
        final Map<String, Float> engMap;
        EngineResult(float weight, Map<String, Float> engMap) {
            this.weight = weight;
            this.engMap = engMap;
        }
    }

    private EngineResult invokeEngine(final MessageLetterCount letterCounts, final Player player,
            final ChatConfig cc, final ChatData data) {
        float wEngine = 0f;
        final Map<String, Float> engMap;
        synchronized (engine) {
            engMap = engine.process(letterCounts, player.getName(), cc, data);
            for (final Float res : engMap.values()) {
                if (cc.textEngineMaximum) {
                    wEngine = Math.max(wEngine, res.floatValue());
                } else {
                    wEngine += res.floatValue();
                }
            }
        }
        return new EngineResult(wEngine, engMap);
    }

    private static final class EvalResult {
        final boolean cancel;
        final float accumulated;
        final float shortTermAccumulated;
        EvalResult(boolean cancel, float accumulated, float shortTermAccumulated) {
            this.cancel = cancel;
            this.accumulated = accumulated;
            this.shortTermAccumulated = shortTermAccumulated;
        }
    }

    private EvalResult evaluateFrequencyAndViolations(final Player player, final ICaptcha captcha,
            final ChatConfig cc, final ChatData data, final IPlayerData pData, final String lcMessage,
            final long time, final float score) {
        float normalScore = Math.max(cc.textFreqNormMin, score);
        data.chatFrequency.add(time, normalScore);
        float accumulated = cc.textFreqNormWeight * data.chatFrequency.score(cc.textFreqNormFactor);
        boolean normalViolation = accumulated > cc.textFreqNormLevel;

        float shortTermScore = Math.max(cc.textFreqShortTermMin, score);
        data.chatShortTermFrequency.add(time, shortTermScore);
        float shortTermAccumulated = cc.textFreqShortTermWeight * data.chatShortTermFrequency.score(cc.textFreqShortTermFactor);
        boolean shortTermViolation = shortTermAccumulated > cc.textFreqShortTermLevel;

        boolean cancel = false;
        if (normalViolation || shortTermViolation) {
            synchronized (globalLock) {
                lastCancelledMessage = lcMessage;
                lastCancelledTime = time;
            }

            final double added = shortTermViolation ? (shortTermAccumulated - cc.textFreqShortTermLevel) / 3.0
                    : (accumulated - cc.textFreqNormLevel) / 10.0;
            data.textVL += added;

            if (captcha.shouldStartCaptcha(player, cc, data, pData)) {
                captcha.sendNewCaptcha(player, cc, data);
                cancel = true;
            } else {
                if (shortTermViolation) {
                    if (executeActions(player, data.textVL, added, cc.textFreqShortTermActions).willCancel()) {
                        cancel = true;
                    }
                } else if (normalViolation) {
                    if (executeActions(player, data.textVL, added, cc.textFreqNormActions).willCancel()) {
                        cancel = true;
                    }
                }
            }
        } else if (cc.chatWarningCheck && time - data.chatWarningTime > cc.chatWarningTimeout
                && (100f * accumulated / cc.textFreqNormLevel > cc.chatWarningLevel
                        || 100f * shortTermAccumulated / cc.textFreqShortTermLevel > cc.chatWarningLevel)) {
            NCPAPIProvider.getNoCheatPlusAPI().sendMessageOnTick(player.getName(),
                    ColorUtil.replaceColors(cc.chatWarningMessage));
            data.chatWarningTime = time;
        } else {
            data.textVL *= 0.95;
            if (cc.textAllowVLReset && normalScore < 2.0f * cc.textFreqNormWeight
                    && shortTermScore < 2.0f * cc.textFreqShortTermWeight) {
                data.textVL = 0.0;
            }
        }
        return new EvalResult(cancel, accumulated, shortTermAccumulated);
    }

}
