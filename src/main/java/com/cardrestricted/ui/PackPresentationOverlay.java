package com.cardrestricted.ui;

import com.cardrestricted.CardRestrictedAccountConfig;
import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.presentation.PackPresentationController;
import com.cardrestricted.presentation.CardArtworkProvider;
import com.cardrestricted.presentation.PackPresentationHitboxes;
import com.cardrestricted.presentation.PackPresentationRenderer;
import com.cardrestricted.presentation.PackPresentationSelection;
import com.cardrestricted.presentation.PackPresentationSnapshot;
import com.cardrestricted.presentation.PackPresentationState;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.IntConsumer;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public final class PackPresentationOverlay extends Overlay
{
    private final Client client;
    private final CardRestrictedAccountConfig config;
    private final PackPresentationController controller;
    private final CardCatalogue catalogue;
    private final CardArtworkProvider artworkProvider;
    private final Executor preparationExecutor;
    private final IntConsumer dealPlacementCue;
    private final Object preparationLock = new Object();
    private volatile PackPresentationRenderer renderer;
    private volatile UUID preparedOpeningId;
    private UUID preparingOpeningId;
    private final Timer animationTimer;
    private volatile PackPresentationHitboxes hitboxes =
        PackPresentationHitboxes.empty();
    private volatile Point hoverPoint;
    private volatile double zoomFactor = 1.0;
    private volatile UUID observedOpeningId;
    private UUID dealCueOpeningId;
    private final boolean[] dealCuePlayed = new boolean[5];

    public PackPresentationOverlay(
        Client client,
        CardRestrictedAccountConfig config,
        CardCatalogue catalogue,
        PackPresentationController controller,
        CardArtworkProvider artworkProvider)
    {
        this(
            client,
            config,
            catalogue,
            controller,
            artworkProvider,
            Runnable::run,
            position -> { });
    }

    public PackPresentationOverlay(
        Client client,
        CardRestrictedAccountConfig config,
        CardCatalogue catalogue,
        PackPresentationController controller,
        CardArtworkProvider artworkProvider,
        Executor preparationExecutor)
    {
        this(
            client,
            config,
            catalogue,
            controller,
            artworkProvider,
            preparationExecutor,
            position -> { });
    }

    public PackPresentationOverlay(
        Client client,
        CardRestrictedAccountConfig config,
        CardCatalogue catalogue,
        PackPresentationController controller,
        CardArtworkProvider artworkProvider,
        Executor preparationExecutor,
        IntConsumer dealPlacementCue)
    {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.artworkProvider = Objects.requireNonNull(
            artworkProvider,
            "artworkProvider");
        this.preparationExecutor = Objects.requireNonNull(
            preparationExecutor,
            "preparationExecutor");
        this.dealPlacementCue = Objects.requireNonNull(
            dealPlacementCue,
            "dealPlacementCue");
        animationTimer = new Timer(33, event -> {
            Timer timer = (Timer) event.getSource();
            if (!isActive())
            {
                timer.stop();
                return;
            }
            controller.setReducedMotion(config.reducedPackMotion());
            controller.tick(System.nanoTime() / 1_000_000L);
            client.getCanvas().repaint();
            if (!controller.needsAnimationTick())
            {
                timer.stop();
            }
        });
        animationTimer.setCoalesce(true);
        animationTimer.setInitialDelay(0);
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGHEST);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        PackPresentationSnapshot snapshot = controller.snapshot();
        UUID currentOpeningId = snapshot.getOpeningId().orElse(null);
        if (!Objects.equals(currentOpeningId, observedOpeningId))
        {
            observedOpeningId = currentOpeningId;
            resetDealCueState(currentOpeningId);
            zoomFactor = 1.0;
            hoverPoint = null;
        }
        if (snapshot.getState() == PackPresentationState.IDLE)
        {
            observedOpeningId = null;
            zoomFactor = 1.0;
            hitboxes = PackPresentationHitboxes.empty();
            return null;
        }
        PackPresentationRenderer activeRenderer = renderer;
        if (activeRenderer == null
            || !Objects.equals(currentOpeningId, preparedOpeningId))
        {
            requestRendererPreparation(snapshot);
            hitboxes = PackPresentationHitboxes.empty();
            return null;
        }
        Dimension canvasSize = client.getCanvas().getSize();
        Rectangle viewportBounds = new Rectangle(
            Math.max(0, client.getViewportXOffset()),
            Math.max(0, client.getViewportYOffset()),
            Math.max(1, Math.min(client.getViewportWidth(), canvasSize.width)),
            Math.max(1, Math.min(client.getViewportHeight(), canvasSize.height)));
        Point currentHover = hoverPoint == null
            ? null
            : new Point(hoverPoint);
        hitboxes = activeRenderer.render(
            graphics,
            canvasSize,
            viewportBounds,
            snapshot,
            currentHover,
            System.nanoTime() / 1_000_000L,
            config.reducedPackMotion(),
            zoomFactor,
            false);
        // Fire placement audio only after this exact deal frame has actually
        // been painted. Earlier timing emitted from the timer before repaint and at
        // local==1.0, which could make sound trail the eased visual contact
        // by more than 100 ms (or run while the renderer was still preparing).
        emitDealPlacementCues(snapshot);
        return null;
    }


    private void resetDealCueState(UUID openingId)
    {
        dealCueOpeningId = openingId;
        java.util.Arrays.fill(dealCuePlayed, false);
    }

    private void emitDealPlacementCues(PackPresentationSnapshot snapshot)
    {
        UUID openingId = snapshot.getOpeningId().orElse(null);
        if (!Objects.equals(openingId, dealCueOpeningId))
        {
            resetDealCueState(openingId);
        }
        if (snapshot.getState() != PackPresentationState.CARD_DEAL)
        {
            return;
        }
        int count = Math.min(5, snapshot.getTotalCards());
        for (int position = 0; position < count; position++)
        {
            // Match the renderer's eased position, not the raw transition.
            // At ~98.5% of the eased travel the card is visually in contact
            // with its final slot; waiting for raw local==1.0 sounds late.
            if (!dealCuePlayed[position]
                && PackPresentationRenderer.dealVisualProgress(
                    snapshot, position) >= 0.985)
            {
                dealCuePlayed[position] = true;
                dealPlacementCue.accept(position);
            }
        }
    }

    void emitDealPlacementCuesForTesting(PackPresentationSnapshot snapshot)
    {
        emitDealPlacementCues(snapshot);
    }


    private void requestRendererPreparation(
        PackPresentationSnapshot snapshot)
    {
        UUID openingId = snapshot.getOpeningId().orElse(null);
        if (openingId == null
            || Objects.equals(openingId, preparedOpeningId))
        {
            return;
        }
        synchronized (preparationLock)
        {
            if (Objects.equals(openingId, preparedOpeningId)
                || Objects.equals(openingId, preparingOpeningId))
            {
                return;
            }
            preparingOpeningId = openingId;
        }
        try
        {
            preparationExecutor.execute(() -> prepareRenderer(
                openingId,
                snapshot));
        }
        catch (RejectedExecutionException exception)
        {
            synchronized (preparationLock)
            {
                if (Objects.equals(openingId, preparingOpeningId))
                {
                    preparingOpeningId = null;
                }
            }
        }
    }

    private void prepareRenderer(
        UUID openingId,
        PackPresentationSnapshot snapshot)
    {
        try
        {
            PackPresentationRenderer prepared =
                new PackPresentationRenderer(
                    catalogue,
                    artworkProvider,
                    FontManager.getRunescapeFont(),
                    FontManager.getRunescapeBoldFont(),
                    FontManager.getRunescapeSmallFont());
            prepared.prewarmCardFronts(snapshot);
            synchronized (preparationLock)
            {
                preparingOpeningId = null;
                UUID currentOpeningId = controller.snapshot()
                    .getOpeningId().orElse(null);
                if (!Objects.equals(openingId, currentOpeningId))
                {
                    return;
                }
                renderer = prepared;
                preparedOpeningId = openingId;
            }
            SwingUtilities.invokeLater(() -> {
                if (isActive()
                    && Objects.equals(openingId, preparedOpeningId))
                {
                    if (controller.needsAnimationTick()
                        && !animationTimer.isRunning())
                    {
                        animationTimer.start();
                    }
                    client.getCanvas().repaint();
                }
            });
        }
        catch (RuntimeException exception)
        {
            synchronized (preparationLock)
            {
                if (Objects.equals(openingId, preparingOpeningId))
                {
                    preparingOpeningId = null;
                }
            }
        }
    }

    boolean isRendererInitializedForTesting()
    {
        return renderer != null;
    }

    public void startAnimationLoop()
    {
        PackPresentationSnapshot snapshot = controller.snapshot();
        requestRendererPreparation(snapshot);
        client.getCanvas().repaint();
        if (!Objects.equals(
                snapshot.getOpeningId().orElse(null),
                preparedOpeningId)
            || !controller.needsAnimationTick()
            || animationTimer.isRunning())
        {
            return;
        }

        Runnable start = () -> {
            if (controller.needsAnimationTick()
                && !animationTimer.isRunning())
            {
                // Avoid an extra invokeLater turn when the pack click already
                // arrived on Swing's EDT. This removes the small dead period
                // between opening the booster and the first CARD_DEAL frame.
                controller.tick(System.nanoTime() / 1_000_000L);
                animationTimer.start();
                client.getCanvas().repaint();
            }
        };
        if (SwingUtilities.isEventDispatchThread())
        {
            start.run();
        }
        else
        {
            SwingUtilities.invokeLater(start);
        }
    }

    public void stopAnimationLoop()
    {
        animationTimer.stop();
    }

    public boolean isActive()
    {
        return controller.getState() != PackPresentationState.IDLE;
    }

    public PackPresentationSelection selectionAt(Point point)
    {
        return hitboxes.resolve(Objects.requireNonNull(point, "point"));
    }

    public void setHoverPoint(Point point)
    {
        hoverPoint = point == null ? null : new Point(point);
    }

    public void clearHoverPoint()
    {
        hoverPoint = null;
    }

    public void adjustZoom(int wheelRotation)
    {
        if (wheelRotation == 0)
        {
            return;
        }
        zoomFactor = clamp(
            zoomFactor * Math.pow(1.10, -wheelRotation),
            0.68,
            1.70);
        client.getCanvas().repaint();
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
