package com.cardrestricted.audio;

import com.cardrestricted.catalog.Rarity;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.audio.AudioPlayer;

public final class AudioCueManager
{
    private final AudioPlayer audioPlayer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService executor =
        Executors.newScheduledThreadPool(3,
        new ThreadFactory()
        {
            private int index;

            @Override
            public synchronized Thread newThread(Runnable runnable)
            {
                Thread thread = new Thread(
                    runnable,
                    "card-locked-audio-" + (++index));
                thread.setDaemon(true);
                return thread;
            }
        });

    /**
     * Production constructor. RuneLite owns the actual audio playback backend.
     */
    public AudioCueManager(AudioPlayer audioPlayer)
    {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer");
    }

    /**
     * Test-only lifecycle constructor. Tests shut this manager down before any
     * playback request can reach the null backend.
     */
    AudioCueManager()
    {
        this.audioPlayer = null;
    }

    public void playDealCard(int cardPosition)
    {
        if (cardPosition < 0 || cardPosition >= 5)
        {
            return;
        }
        // One physical deck-to-table cue is fired by the presentation
        // overlay when the corresponding card actually reaches its laid-out
        // position. Keeping timing in the visual state machine prevents audio
        // from running ahead of, or continuing after, a skipped deal.
        play("card_deal_" + (cardPosition + 1) + ".wav", -1.4f);
    }

    public void playReveal(Rarity rarity)
    {
        playReveal(rarity, false);
    }

    public void playReveal(Rarity rarity, boolean foil)
    {
        Objects.requireNonNull(rarity, "rarity");
        if (foil)
        {
            play("reveal_foil.wav", -1.4f);
            return;
        }
        switch (rarity)
        {
            case COMMON:
                play("reveal_common.wav", -1.8f);
                break;
            case UNCOMMON:
                play("reveal_uncommon.wav", -1.7f);
                break;
            case RARE:
                play("reveal_rare.wav", -1.6f);
                break;
            case EPIC:
                play("reveal_epic.wav", -1.5f);
                break;
            case LEGENDARY:
                play("reveal_legendary.wav", -1.4f);
                break;
            case MYTHIC:
                play("reveal_mythic.wav", -1.3f);
                break;
            case GODLY:
                play("reveal_godly.wav", -1.2f);
                break;
            default:
                play("reveal_common.wav", -1.8f);
                break;
        }
    }

    public void shutdown()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        // Audio is cosmetic. Never hold RuneLite's Swing EDT waiting for an
        // audio worker during plugin disable/reload.
        executor.shutdownNow();
    }

    boolean isShutdownForTesting()
    {
        return closed.get() && executor.isShutdown();
    }

    private void play(String resourceName, float gainDb)
    {
        if (closed.get())
        {
            return;
        }
        try
        {
            executor.execute(() -> playNow(resourceName, gainDb));
        }
        catch (RejectedExecutionException ignored)
        {
            // Shutdown can race cosmetic audio requests.
        }
    }

    private void playNow(String resourceName, float gainDb)
    {
        if (closed.get() || audioPlayer == null)
        {
            return;
        }
        try
        {
            // The WAV files live beside this class in the resource tree, so a
            // relative class resource name gives RuneLite's AudioPlayer the
            // same packaged cue without using javax.sound directly.
            audioPlayer.play(AudioCueManager.class, resourceName, gainDb);
        }
        catch (Exception ignored)
        {
            // Audio is cosmetic. Fail silently so gameplay is unaffected.
        }
    }
}
