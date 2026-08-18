package com.cardrestricted.ui;

import com.cardrestricted.CardRestrictedAccountConfig;
import com.cardrestricted.collection.achievement.AchievementDefinition;
import com.cardrestricted.presentation.AchievementToastController;
import com.cardrestricted.presentation.AchievementToastRenderer;
import com.cardrestricted.presentation.AchievementToastSnapshot;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public final class AchievementToastOverlay extends Overlay
{
    private static final int FRAME_DELAY_MILLIS = 25;

    private final Client client;
    private final CardRestrictedAccountConfig config;
    private final AchievementToastController controller;
    private final AchievementToastRenderer renderer;
    private final BooleanSupplier presentationBlocked;
    private final Timer animationTimer;

    public AchievementToastOverlay(
        Client client,
        CardRestrictedAccountConfig config,
        AchievementToastController controller,
        BooleanSupplier presentationBlocked)
    {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.presentationBlocked = Objects.requireNonNull(
            presentationBlocked,
            "presentationBlocked");
        this.renderer = new AchievementToastRenderer(
            FontManager.getRunescapeFont(),
            FontManager.getRunescapeBoldFont(),
            FontManager.getRunescapeSmallFont());
        animationTimer = new Timer(FRAME_DELAY_MILLIS, event -> animate());
        animationTimer.setCoalesce(true);
        animationTimer.setInitialDelay(0);
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGHEST - 1.0f);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.achievementToasts()
            || presentationBlocked.getAsBoolean())
        {
            return null;
        }
        AchievementToastSnapshot snapshot = controller.snapshot();
        if (!snapshot.isActive())
        {
            return null;
        }
        Dimension canvasSize = client.getCanvas().getSize();
        Rectangle viewportBounds = new Rectangle(
            Math.max(0, client.getViewportXOffset()),
            Math.max(0, client.getViewportYOffset()),
            Math.max(1, Math.min(client.getViewportWidth(), canvasSize.width)),
            Math.max(1, Math.min(client.getViewportHeight(), canvasSize.height)));
        renderer.render(graphics, viewportBounds, snapshot);
        return null;
    }

    public void enqueue(Collection<AchievementDefinition> achievements)
    {
        Objects.requireNonNull(achievements, "achievements");
        Runnable operation = () -> {
            if (!config.achievementToasts())
            {
                return;
            }
            controller.setReducedMotion(config.reducedPackMotion());
            controller.enqueue(achievements);
            if (controller.isActive() && !animationTimer.isRunning())
            {
                animationTimer.start();
            }
            client.getCanvas().repaint();
        };
        if (SwingUtilities.isEventDispatchThread())
        {
            operation.run();
        }
        else
        {
            SwingUtilities.invokeLater(operation);
        }
    }

    public void clear()
    {
        Runnable operation = () -> {
            animationTimer.stop();
            controller.clear();
            client.getCanvas().repaint();
        };
        if (SwingUtilities.isEventDispatchThread())
        {
            operation.run();
        }
        else
        {
            SwingUtilities.invokeLater(operation);
        }
    }

    public void stopAnimationLoop()
    {
        animationTimer.stop();
    }

    private void animate()
    {
        if (!config.achievementToasts())
        {
            animationTimer.stop();
            controller.clear();
            client.getCanvas().repaint();
            return;
        }
        long nowMillis = System.nanoTime() / 1_000_000L;
        controller.setReducedMotion(config.reducedPackMotion());
        if (presentationBlocked.getAsBoolean())
        {
            animationTimer.setDelay(150);
            controller.synchroniseClock(nowMillis);
            return;
        }
        animationTimer.setDelay(FRAME_DELAY_MILLIS);
        controller.tick(nowMillis);
        client.getCanvas().repaint();
        if (!controller.isActive())
        {
            animationTimer.stop();
        }
    }
}
