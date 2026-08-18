package com.cardrestricted.presentation;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CatalogueTextQuality;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.pack.PackCardResult;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import javax.imageio.ImageIO;

public final class PackPresentationRenderer
{
    private static final Color SCRIM = new Color(4, 6, 11, 220);
    private static final Color GOLD = new Color(214, 169, 73);
    private static final Color PALE_GOLD = new Color(247, 224, 156);
    private static final Color MUTED = new Color(176, 180, 191);
    private static final Color PANEL = new Color(26, 28, 35, 242);
    private static final Color SUCCESS = new Color(101, 207, 126);
    private static final Color DUPLICATE = new Color(244, 164, 64);
    private static final Color INK = new Color(18, 17, 20);
    private static final int CARD_COUNT = 5;
    private static final int CARD_SURFACE_WIDTH = 1024;
    private static final int CARD_SURFACE_HEIGHT = 1536;
    private static final int FRONT_CACHE_WIDTH = 512;
    private static final int FRONT_CACHE_HEIGHT = 768;
    private static final int PACK_SURFACE_WIDTH = 1024;
    private static final int PACK_SURFACE_HEIGHT = 1536;

    private final CardCatalogue catalogue;
    private final CardArtworkProvider artworkProvider;
    private final Font regularFont;
    private final Font boldFont;
    private final Font smallFont;
    private final double[] hoverFocus = new double[CARD_COUNT];
    private final Map<String, BufferedImage> displaySurfaceCache =
        new LinkedHashMap<String, BufferedImage>(32, 0.75f, true)
        {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(
                Map.Entry<String, BufferedImage> eldest)
            {
                return size() > 96;
            }
        };
    private final Map<String, BufferedImage> cardFrontSurfaceCache =
        new LinkedHashMap<String, BufferedImage>(12, 0.75f, true)
        {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(
                Map.Entry<String, BufferedImage> eldest)
            {
                return size() > 12;
            }
        };
    private BufferedImage cardBackSurface;
    private BufferedImage boosterSurface;
    private BufferedImage openedBoosterSurface;
    private final Map<Rarity, BufferedImage> frameAssets =
        new EnumMap<>(Rarity.class);
    private long previousAnimationTimeMillis = -1L;

    public PackPresentationRenderer(CardCatalogue catalogue)
    {
        this(
            catalogue,
            CardArtworkProvider.none(),
            new Font(Font.SERIF, Font.PLAIN, 16),
            new Font(Font.SERIF, Font.BOLD, 16),
            new Font(Font.SERIF, Font.PLAIN, 14));
    }

    public PackPresentationRenderer(
        CardCatalogue catalogue,
        Font regularFont,
        Font boldFont,
        Font smallFont)
    {
        this(
            catalogue,
            CardArtworkProvider.none(),
            regularFont,
            boldFont,
            smallFont);
    }

    public PackPresentationRenderer(
        CardCatalogue catalogue,
        CardArtworkProvider artworkProvider,
        Font regularFont,
        Font boldFont,
        Font smallFont)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.artworkProvider = Objects.requireNonNull(artworkProvider, "artworkProvider");
        this.regularFont = Objects.requireNonNull(
            regularFont,
            "regularFont");
        this.boldFont = Objects.requireNonNull(boldFont, "boldFont");
        this.smallFont = Objects.requireNonNull(smallFont, "smallFont");
        loadVisualAssets();
        cardBackSurface();
        boosterSurface();
    }

    public PackPresentationHitboxes render(
        Graphics2D graphics,
        Dimension canvasSize,
        PackPresentationSnapshot snapshot,
        Point hoverPoint,
        long animationTimeMillis,
        boolean reducedMotion)
    {
        return render(
            graphics,
            canvasSize,
            snapshot,
            hoverPoint,
            animationTimeMillis,
            reducedMotion,
            1.0);
    }

    public PackPresentationHitboxes render(
        Graphics2D graphics,
        Dimension canvasSize,
        PackPresentationSnapshot snapshot,
        Point hoverPoint,
        long animationTimeMillis,
        boolean reducedMotion,
        double zoomFactor)
    {
        return render(
            graphics,
            canvasSize,
            new Rectangle(0, 0, canvasSize.width, canvasSize.height),
            snapshot,
            hoverPoint,
            animationTimeMillis,
            reducedMotion,
            zoomFactor);
    }

    public PackPresentationHitboxes render(
        Graphics2D graphics,
        Dimension canvasSize,
        Rectangle viewportBounds,
        PackPresentationSnapshot snapshot,
        Point hoverPoint,
        long animationTimeMillis,
        boolean reducedMotion,
        double zoomFactor)
    {
        return render(
            graphics,
            canvasSize,
            viewportBounds,
            snapshot,
            hoverPoint,
            animationTimeMillis,
            reducedMotion,
            zoomFactor,
            false);
    }

    public PackPresentationHitboxes render(
        Graphics2D graphics,
        Dimension canvasSize,
        Rectangle viewportBounds,
        PackPresentationSnapshot snapshot,
        Point hoverPoint,
        long animationTimeMillis,
        boolean reducedMotion,
        double zoomFactor,
        boolean diagnosticsEnabled)
    {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(canvasSize, "canvasSize");
        Objects.requireNonNull(viewportBounds, "viewportBounds");
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.getState() == PackPresentationState.IDLE
            || canvasSize.width < 1
            || canvasSize.height < 1
            || viewportBounds.width < 1
            || viewportBounds.height < 1)
        {
            return PackPresentationHitboxes.empty();
        }

        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            configure(g);
            g.setColor(SCRIM);
            g.fillRect(0, 0, canvasSize.width, canvasSize.height);
            Layout layout = Layout.create(
                viewportBounds,
                zoomFactor,
                snapshot.getTotalCards());
            double frameSeconds = animationFrameSeconds(
                animationTimeMillis);
            Rectangle skip = new Rectangle();
            Rectangle close = new Rectangle();
            Rectangle pack = new Rectangle();
            List<PackPresentationHitboxes.CardHitbox> cards =
                new ArrayList<>();

            switch (snapshot.getState())
            {
                case PACK_ENTER:
                    drawWrapperEntrance(
                        g,
                        layout,
                        snapshot.getTransitionProgress(),
                        reducedMotion);
                    skip = layout.skipButton;
                    drawButton(g, skip, "SKIP", contains(skip, hoverPoint), false);
                    break;
                case PACK_OPEN:
                    drawWrapperReady(
                        g,
                        layout,
                        hoverPoint,
                        animationTimeMillis,
                        reducedMotion);
                    pack = layout.wrapper;
                    break;
                case CARD_DEAL:
                    drawDeal(g, layout, snapshot, reducedMotion);
                    skip = layout.skipButton;
                    drawButton(g, skip, "SKIP", contains(skip, hoverPoint), false);
                    break;
                case READY_TO_REVEAL:
                    drawCardRow(
                        g,
                        layout,
                        snapshot,
                        hoverPoint,
                        animationTimeMillis,
                        frameSeconds,
                        reducedMotion);
                    for (int position = 0;
                         position < snapshot.getTotalCards();
                         position++)
                    {
                        if (snapshot.canRevealPosition(position))
                        {
                            cards.add(new PackPresentationHitboxes.CardHitbox(
                                position,
                                layout.cardBounds(position)));
                        }
                    }
                    if (snapshot.hasActiveFlips())
                    {
                        skip = layout.skipButton;
                        drawButton(
                            g,
                            skip,
                            "SKIP FLIPS",
                            contains(skip, hoverPoint),
                            false);
                    }
                    break;
                case CARD_FLIP:
                    drawCardRow(
                        g,
                        layout,
                        snapshot,
                        hoverPoint,
                        animationTimeMillis,
                        frameSeconds,
                        reducedMotion);
                    break;
                case SUMMARY:
                    drawCardRow(
                        g,
                        layout,
                        snapshot,
                        hoverPoint,
                        animationTimeMillis,
                        frameSeconds,
                        reducedMotion);
                    break;
                case IDLE:
                default:
                    break;
            }

            if (diagnosticsEnabled)
            {
                drawPackDiagnostics(g, layout, snapshot);
            }

            Rectangle dismissBackground = new Rectangle();
            List<Rectangle> dismissExclusions = Collections.emptyList();
            if (snapshot.getState() == PackPresentationState.SUMMARY)
            {
                dismissBackground = new Rectangle(
                    0,
                    0,
                    canvasSize.width,
                    canvasSize.height);
                dismissExclusions = new ArrayList<>();
                for (int position = 0;
                     position < snapshot.getTotalCards();
                     position++)
                {
                    dismissExclusions.add(expand(
                        layout.cardBounds(position),
                        Math.max(7, layout.cardBounds(position).width / 16)));
                }
            }

            return new PackPresentationHitboxes(
                skip,
                close,
                pack,
                cards,
                dismissBackground,
                dismissExclusions);
        }
        finally
        {
            g.dispose();
        }
    }

    private void drawHeading(
        Graphics2D g,
        Layout layout,
        PackPresentationSnapshot snapshot)
    {
        if (snapshot.getState() == PackPresentationState.IDLE)
        {
            return;
        }
        String title = snapshot.getPackDisplayName().toUpperCase(Locale.ROOT);
        String instruction;
        switch (snapshot.getState())
        {
            case PACK_ENTER:
                instruction = "PACK READY";
                break;
            case PACK_OPEN:
                instruction = "LEFT CLICK THE BOOSTER TO OPEN  |  SCROLL TO ZOOM";
                break;
            case CARD_DEAL:
                instruction = "DEALING CARDS";
                break;
            case READY_TO_REVEAL:
                instruction = snapshot.canRevealAny()
                    ? snapshot.getProgressLabel()
                        + "  |  CLICK ANY CARD  |  SPACE REVEALS ALL"
                    : snapshot.getProgressLabel() + "  |  REVEALING...";
                break;
            case CARD_FLIP:
                instruction = snapshot.getProgressLabel() + "  |  REVEALING...";
                break;
            case SUMMARY:
                instruction = snapshot.getProgressLabel()
                    + "  |  PACK COMPLETE";
                break;
            default:
                instruction = snapshot.getProgressLabel();
                break;
        }

        int panelX = layout.centerX - layout.headingPanelWidth / 2;
        int panelY = layout.headingPanelY;
        g.setColor(new Color(12, 14, 20, 210));
        g.fillRoundRect(
            panelX,
            panelY,
            layout.headingPanelWidth,
            layout.headingPanelHeight,
            12,
            12);
        g.setColor(new Color(214, 169, 73, 185));
        g.drawRoundRect(
            panelX,
            panelY,
            layout.headingPanelWidth,
            layout.headingPanelHeight,
            12,
            12);

        g.setFont(bold(layout.titleSize));
        g.setColor(PALE_GOLD);
        drawCentred(g, title, layout.centerX, layout.titleY);
        g.setFont(bold(layout.subtitleSize));
        g.setColor(Color.WHITE);
        drawCentred(g, instruction, layout.centerX, layout.subtitleY);
    }

    private void drawWrapperEntrance(
        Graphics2D g,
        Layout layout,
        double progress,
        boolean reducedMotion)
    {
        double eased = easeInOutCubic(progress);
        double scaleValue = reducedMotion ? 1.0 : 0.92 + 0.08 * eased;
        int offsetY = reducedMotion
            ? 0
            : (int) Math.round((1.0 - eased) * 20.0);
        Rectangle bounds = scale(
            layout.wrapper,
            layout.wrapper.getCenterX(),
            layout.wrapper.getCenterY() + offsetY,
            scaleValue,
            scaleValue);
        withAlpha(g, (float) eased, () ->
            drawWrapper(g, bounds, false));
    }

    private void drawWrapperReady(
        Graphics2D g,
        Layout layout,
        Point hoverPoint,
        long animationTimeMillis,
        boolean reducedMotion)
    {
        boolean hovered = contains(layout.wrapper, hoverPoint);
        double idlePulse = reducedMotion
            ? 0.0
            : Math.sin(animationTimeMillis / 420.0) * 0.010;
        int idleLift = reducedMotion
            ? 0
            : (int) Math.round(Math.cos(animationTimeMillis / 520.0) * 3.0);
        double scale = 1.0 + idlePulse + (hovered ? 0.028 : 0.0);
        Rectangle wrapper = scale(
            layout.wrapper,
            layout.wrapper.getCenterX(),
            layout.wrapper.getCenterY() - idleLift,
            scale,
            scale);
        drawWrapper(g, wrapper, hovered);
    }

    private void drawDeal(
        Graphics2D g,
        Layout layout,
        PackPresentationSnapshot snapshot,
        boolean reducedMotion)
    {
        double progress = snapshot.getTransitionProgress();
        Rectangle fadingWrapper = scale(
            layout.wrapper,
            layout.wrapper.getCenterX(),
            layout.wrapper.getCenterY(),
            1.0 - progress * 0.08,
            1.0 - progress * 0.08);
        withAlpha(g, (float) Math.max(0.0, 1.0 - progress * 1.1), () ->
            drawDisplaySurface(
                g,
                resizedSurface(
                    "booster-opened",
                    openedBoosterSurface(),
                    fadingWrapper),
                fadingWrapper));

        for (int position = 0;
             position < snapshot.getTotalCards();
             position++)
        {
            double local = dealLocalProgress(snapshot, position);
            Rectangle destination = layout.cardBounds(position);
            if (reducedMotion)
            {
                withAlpha(g, (float) local, () ->
                    drawCardBack(g, destination, false));
                continue;
            }
            double eased = easeOutCubic(local);
            int startX = layout.wrapper.x + layout.wrapper.width / 2
                - destination.width / 2;
            int startY = layout.wrapper.y + layout.wrapper.height / 2
                - destination.height / 2;
            Rectangle current = new Rectangle(
                lerp(startX, destination.x, eased),
                lerp(startY, destination.y, eased),
                destination.width,
                destination.height);
            current = scale(
                current,
                current.getCenterX(),
                current.getCenterY(),
                0.72 + eased * 0.28,
                0.72 + eased * 0.28);
            drawCardBack(g, current, false);
        }
    }


    public static double dealLocalProgress(
        PackPresentationSnapshot snapshot,
        int position)
    {
        Objects.requireNonNull(snapshot, "snapshot");
        if (position < 0 || position >= snapshot.getTotalCards())
        {
            return 0.0;
        }
        return clamp(
            snapshot.getTransitionProgress() * 1.45
                - position * 0.105);
    }

    /** Eased travel used by both rendering and synchronized placement audio. */
    public static double dealVisualProgress(
        PackPresentationSnapshot snapshot,
        int position)
    {
        return easeOutCubic(dealLocalProgress(snapshot, position));
    }

    private void drawCardRow(
        Graphics2D g,
        Layout layout,
        PackPresentationSnapshot snapshot,
        Point hoverPoint,
        long animationTimeMillis,
        double frameSeconds,
        boolean reducedMotion)
    {
        for (int position = 0;
             position < snapshot.getTotalCards();
             position++)
        {
            Rectangle bounds = layout.cardBounds(position);
            PackCardResult result = snapshot.getCardResults().get(position);
            boolean hovered = contains(bounds, hoverPoint);
            float hoverGlow = hovered
                ? hoverGlowIntensity(animationTimeMillis, position, reducedMotion)
                : 0.0f;

            if (snapshot.isFlipping(position))
            {
                updateHoverFocus(position, false, frameSeconds, reducedMotion);
                drawFlip(
                    g,
                    bounds,
                    result,
                    snapshot.getFlipProgress(position),
                    reducedMotion);
                drawOutcomeBelow(
                    g,
                    layout.outcomeBounds(position),
                    result,
                    true);
                drawRarityEffect(
                    g,
                    bounds,
                    result,
                    animationTimeMillis,
                    position,
                    snapshot.getOpeningId().orElse(null),
                    snapshot.getFlipProgress(position));
                continue;
            }

            if (snapshot.isRevealed(position))
            {
                updateHoverFocus(position, false, frameSeconds, reducedMotion);
                drawPersistentRarityAura(
                    g,
                    bounds,
                    result,
                    animationTimeMillis,
                    position,
                    reducedMotion);
                if (hovered)
                {
                    drawFocusedCardFront(
                        g,
                        bounds,
                        result,
                        hoverGlow,
                        reducedMotion);
                }
                else
                {
                    drawCardFront(g, bounds, result);
                }
                drawPersistentRarityTwinkles(
                    g,
                    bounds,
                    result,
                    animationTimeMillis,
                    position,
                    reducedMotion);
                drawOutcomeBelow(
                    g,
                    layout.outcomeBounds(position),
                    result,
                    false);
                continue;
            }

            boolean selectable = snapshot.canRevealPosition(position);
            boolean requested = snapshot.isRequested(position);
            double hoverFocus = updateHoverFocus(
                position,
                hovered && selectable,
                frameSeconds,
                reducedMotion);
            double zoom = 1.0 + hoverFocus * (reducedMotion ? 0.025 : 0.05);
            Rectangle renderBounds = hoverFocus > 0.001
                ? scale(
                    bounds,
                    bounds.getCenterX(),
                    bounds.getCenterY(),
                    zoom,
                    zoom)
                : bounds;
            if (hovered && selectable)
            {
                drawHiddenHoverGlow(g, renderBounds, result, hoverGlow);
            }
            drawCardBack(g, renderBounds, selectable);
            drawUnrevealedHint(
                g,
                layout.outcomeBounds(position),
                selectable,
                requested);
        }
    }

    private void drawFlip(
        Graphics2D g,
        Rectangle bounds,
        PackCardResult result,
        double progress,
        boolean reducedMotion)
    {
        if (result.isFoil())
        {
            drawFoilFlip(g, bounds, result, progress, reducedMotion);
            return;
        }
        double value = easeInOutCubic(clamp(progress));
        if (reducedMotion)
        {
            if (value < 0.5)
            {
                withAlpha(g, (float) (1.0 - value * 2.0), () ->
                    drawCardBack(g, bounds, false));
            }
            else
            {
                withAlpha(g, (float) ((value - 0.5) * 2.0), () ->
                    drawCardFront(g, bounds, result));
            }
            return;
        }
        double widthScale = Math.max(
            0.035,
            Math.abs(Math.cos(Math.PI * value)));
        Rectangle flipped = scale(
            bounds,
            bounds.getCenterX(),
            bounds.getCenterY(),
            widthScale,
            1.0);
        if (value < 0.5)
        {
            drawCardBack(g, flipped, false);
        }
        else
        {
            drawCardFront(g, flipped, result);
        }
    }

    private void drawFoilFlip(
        Graphics2D g,
        Rectangle bounds,
        PackCardResult result,
        double progress,
        boolean reducedMotion)
    {
        double value = clamp(progress);
        if (reducedMotion)
        {
            double faded = easeInOutCubic(value);
            Rectangle enlarged = scale(
                bounds,
                bounds.getCenterX(),
                bounds.getCenterY(),
                1.0 + Math.sin(Math.PI * faded) * 0.10,
                1.0 + Math.sin(Math.PI * faded) * 0.10);
            if (faded < 0.5)
            {
                drawCardBack(g, enlarged, false);
            }
            else
            {
                drawCardFront(g, enlarged, result);
            }
            return;
        }

        if (value < 0.40)
        {
            double charge = easeOutCubic(value / 0.40);
            double heartbeat = Math.sin(charge * Math.PI * 5.0)
                * (0.010 + charge * 0.018);
            double zoom = 1.0 + charge * 0.44 + heartbeat;
            int lift = (int) Math.round(-16.0 * charge);
            Rectangle approaching = scale(
                bounds,
                bounds.getCenterX(),
                bounds.getCenterY() + lift,
                zoom,
                zoom);
            drawCardBack(g, approaching, false);
            return;
        }

        if (value < 0.68)
        {
            double local = easeInOutCubic((value - 0.40) / 0.28);
            double zoom = 1.44 - local * 0.18;
            double widthScale = Math.max(
                0.020,
                Math.abs(Math.cos(Math.PI * local)));
            int lift = (int) Math.round(
                -16.0 - Math.sin(Math.PI * local) * 12.0);
            Rectangle flipping = scale(
                bounds,
                bounds.getCenterX(),
                bounds.getCenterY() + lift,
                zoom * widthScale,
                zoom);
            if (local < 0.5)
            {
                drawCardBack(g, flipping, false);
            }
            else
            {
                drawCardFront(g, flipping, result);
            }
            return;
        }

        if (value < 0.79)
        {
            double anticipation = (value - 0.68) / 0.11;
            double pulse = Math.sin(Math.PI * anticipation) * 0.025;
            double zoom = 1.26 + pulse;
            int lift = (int) Math.round(-28.0 - anticipation * 5.0);
            Rectangle charged = scale(
                bounds,
                bounds.getCenterX(),
                bounds.getCenterY() + lift,
                zoom,
                zoom);
            drawCardFront(g, charged, result);
            return;
        }

        if (value < 0.91)
        {
            double slam = easeOutCubic((value - 0.79) / 0.12);
            double zoom = 1.26 - slam * 0.31;
            int drop = (int) Math.round(-33.0 + slam * 42.0);
            Rectangle landing = scale(
                bounds,
                bounds.getCenterX(),
                bounds.getCenterY() + drop,
                zoom,
                zoom);
            drawCardFront(g, landing, result);
            return;
        }

        double settle = easeOutCubic((value - 0.91) / 0.09);
        double rebound = Math.sin(Math.PI * settle) * 0.052;
        double finalScale = 0.95 + settle * 0.05 + rebound;
        int settleY = (int) Math.round((1.0 - settle) * 9.0);
        Rectangle settled = scale(
            bounds,
            bounds.getCenterX(),
            bounds.getCenterY() + settleY,
            finalScale,
            finalScale);
        drawCardFront(g, settled, result);
    }

    private void drawFocusedCardFront(
        Graphics2D g,
        Rectangle bounds,
        PackCardResult result,
        double glowIntensity,
        boolean reducedMotion)
    {
        if (glowIntensity <= 0.001)
        {
            drawCardFront(g, bounds, result);
            return;
        }

        CardDefinition card = catalogue.requireCard(result.getCardId());
        Color glow = rarityColor(card.getRarity());
        Graphics2D cardGraphics = (Graphics2D) g.create();
        try
        {
            drawGlow(cardGraphics, bounds, glow, (float) glowIntensity);
            drawCardFront(cardGraphics, bounds, result);
            cardGraphics.setColor(withAlpha(
                glow,
                Math.max(70, Math.min(145, (int) Math.round(glowIntensity * 120.0)))));
            cardGraphics.setStroke(new BasicStroke(1.4f));
            cardGraphics.drawRoundRect(
                bounds.x - 1,
                bounds.y - 1,
                bounds.width + 2,
                bounds.height + 2,
                17,
                17);
        }
        finally
        {
            cardGraphics.dispose();
        }
    }

    private void drawHiddenHoverGlow(
        Graphics2D g,
        Rectangle bounds,
        PackCardResult result,
        double glowIntensity)
    {
        CardDefinition card = catalogue.requireCard(result.getCardId());
        drawGlow(g, bounds, rarityColor(card.getRarity()), (float) glowIntensity);
    }

    private void drawGlow(
        Graphics2D g,
        Rectangle bounds,
        Color glow,
        float intensity)
    {
        for (int ring = 6; ring >= 1; ring--)
        {
            int spread = 4 + ring * 3;
            int alpha = Math.max(
                7,
                Math.min(85, (int) Math.round(intensity * (6.0 + ring * 8.0))));
            g.setColor(withAlpha(glow, alpha));
            g.fillRoundRect(
                bounds.x - spread,
                bounds.y - spread,
                bounds.width + spread * 2,
                bounds.height + spread * 2,
                18 + spread,
                18 + spread);
        }
    }

    private float hoverGlowIntensity(
        long animationTimeMillis,
        int position,
        boolean reducedMotion)
    {
        if (reducedMotion)
        {
            return 0.64f;
        }
        double phase = animationTimeMillis / 230.0 + position * 0.38;
        double pulse = 0.5 + 0.5 * Math.sin(phase);
        return (float) (0.46 + pulse * 0.22);
    }

    private double updateHoverFocus(
        int position,
        boolean hovered,
        double frameSeconds,
        boolean reducedMotion)
    {
        if (position < 0 || position >= hoverFocus.length)
        {
            return 0.0;
        }
        double target = hovered ? 1.0 : 0.0;
        if (reducedMotion)
        {
            hoverFocus[position] = target;
            return target;
        }
        double rate = hovered ? 11.0 : 8.0;
        double blend = 1.0 - Math.exp(-rate * frameSeconds);
        hoverFocus[position] += (target - hoverFocus[position]) * blend;
        if (Math.abs(target - hoverFocus[position]) < 0.002)
        {
            hoverFocus[position] = target;
        }
        return hoverFocus[position];
    }

    private void loadVisualAssets()
    {
        boosterSurface = loadAsset(
            "/com/cardrestricted/ui/booster-sealed.png",
            PACK_SURFACE_WIDTH,
            PACK_SURFACE_HEIGHT);
        openedBoosterSurface = boosterSurface;
        cardBackSurface = loadAsset(
            "/com/cardrestricted/ui/card-back.png",
            CARD_SURFACE_WIDTH,
            CARD_SURFACE_HEIGHT);
        for (Rarity rarity : Rarity.values())
        {
            frameAssets.put(
                rarity,
                loadAsset(
                    "/com/cardrestricted/ui/card-frame-"
                        + rarity.name().toLowerCase(Locale.ROOT)
                        + ".png",
                    CARD_SURFACE_WIDTH,
                    CARD_SURFACE_HEIGHT));
        }
    }

    private BufferedImage loadAsset(
        String resourcePath,
        int targetWidth,
        int targetHeight)
    {
        try (InputStream stream = getClass().getResourceAsStream(resourcePath))
        {
            if (stream == null)
            {
                return null;
            }
            BufferedImage source = ImageIO.read(stream);
            if (source == null)
            {
                return null;
            }
            BufferedImage scaled = new BufferedImage(
                targetWidth,
                targetHeight,
                BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            try
            {
                configure(graphics);
                graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(
                    source,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    null);
            }
            finally
            {
                graphics.dispose();
            }
            return scaled;
        }
        catch (IOException exception)
        {
            return null;
        }
    }

    private void drawWrapper(
        Graphics2D g,
        Rectangle bounds,
        boolean hovered)
    {
        if (hovered)
        {
            for (int ring = 5; ring >= 1; ring--)
            {
                int spread = ring * 3;
                g.setColor(new Color(247, 224, 156, 10 + ring * 3));
                g.fillRoundRect(
                    bounds.x - spread,
                    bounds.y - spread,
                    bounds.width + spread * 2,
                    bounds.height + spread * 2,
                    22 + spread,
                    22 + spread);
            }
        }
        drawDisplaySurface(
            g,
            resizedSurface("booster", boosterSurface(), bounds),
            bounds);
        if (hovered)
        {
            g.setColor(PALE_GOLD);
            g.setStroke(new BasicStroke(2.2f));
            g.drawRoundRect(
                bounds.x - 2,
                bounds.y - 2,
                bounds.width + 4,
                bounds.height + 4,
                20,
                20);
        }
    }

    private BufferedImage boosterSurface()
    {
        if (boosterSurface != null)
        {
            return boosterSurface;
        }
        BufferedImage image = new BufferedImage(
            PACK_SURFACE_WIDTH,
            PACK_SURFACE_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            configure(g);
            Rectangle bounds = new Rectangle(
                18,
                30,
                PACK_SURFACE_WIDTH - 36,
                PACK_SURFACE_HEIGHT - 60);
            RoundRectangle2D shape = new RoundRectangle2D.Double(
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                22,
                22);
            g.setPaint(new GradientPaint(
                bounds.x,
                bounds.y,
                new Color(205, 173, 112),
                bounds.x,
                bounds.y + bounds.height,
                new Color(141, 105, 57)));
            g.fill(shape);
            g.setColor(new Color(79, 54, 25));
            g.setStroke(new BasicStroke(8f));
            g.draw(shape);

            g.setColor(new Color(75, 51, 23, 210));
            for (int x = bounds.x + 8; x < bounds.x + bounds.width - 8; x += 19)
            {
                g.fillPolygon(
                    new int[]{x, x + 9, x + 18},
                    new int[]{bounds.y, bounds.y - 18, bounds.y},
                    3);
                g.fillPolygon(
                    new int[]{x, x + 9, x + 18},
                    new int[]{bounds.y + bounds.height,
                        bounds.y + bounds.height + 18,
                        bounds.y + bounds.height},
                    3);
            }

            int inner = 52;
            g.setColor(new Color(246, 226, 184, 36));
            g.fillRoundRect(
                bounds.x + inner,
                bounds.y + inner,
                bounds.width - inner * 2,
                bounds.height - inner * 2,
                34,
                34);
            g.setColor(new Color(90, 61, 27, 175));
            g.setStroke(new BasicStroke(5f));
            g.drawRoundRect(
                bounds.x + inner,
                bounds.y + inner,
                bounds.width - inner * 2,
                bounds.height - inner * 2,
                34,
                34);

            drawCornerOrnaments(g, bounds, new Color(92, 62, 27));
            drawShieldStar(
                g,
                PACK_SURFACE_WIDTH / 2,
                PACK_SURFACE_HEIGHT * 42 / 100,
                230,
                new Color(42, 39, 32),
                new Color(169, 119, 51),
                new Color(238, 194, 91));

            g.setFont(bold(70));
            g.setColor(new Color(66, 42, 18));
            drawCentred(
                g,
                "BOOSTER PACK",
                PACK_SURFACE_WIDTH / 2,
                PACK_SURFACE_HEIGHT - 176);
            g.setFont(regular(42));
            g.setColor(new Color(77, 51, 22));
            drawCentred(
                g,
                "CARD LOCKED",
                PACK_SURFACE_WIDTH / 2,
                PACK_SURFACE_HEIGHT - 106);
        }
        finally
        {
            g.dispose();
        }
        boosterSurface = image;
        return image;
    }

    private BufferedImage openedBoosterSurface()
    {
        return boosterSurface();
    }

    private void drawWrapperHalf(
        Graphics2D g,
        Rectangle bounds,
        boolean left)
    {
        g.setPaint(new GradientPaint(
            bounds.x,
            bounds.y,
            new Color(55, 31, 63),
            bounds.x,
            bounds.y + bounds.height,
            new Color(14, 22, 40)));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 16, 16);
        int edgeX = left ? bounds.x + bounds.width : bounds.x;
        g.setColor(PALE_GOLD);
        for (int y = bounds.y + 8;
             y < bounds.y + bounds.height - 8;
             y += 14)
        {
            int direction = ((y / 14) & 1) == 0 ? 5 : -5;
            g.drawLine(edgeX, y, edgeX + direction, y + 7);
        }
    }

    private void drawCardBack(
        Graphics2D g,
        Rectangle bounds,
        boolean active)
    {
        drawDisplaySurface(
            g,
            resizedSurface("card-back", cardBackSurface(), bounds),
            bounds);
        if (active)
        {
            g.setColor(new Color(247, 224, 156, 150));
            g.setStroke(new BasicStroke(1.7f));
            g.drawRoundRect(
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                15,
                15);
        }
    }

    private BufferedImage cardBackSurface()
    {
        if (cardBackSurface != null)
        {
            return cardBackSurface;
        }
        BufferedImage image = new BufferedImage(
            CARD_SURFACE_WIDTH,
            CARD_SURFACE_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            configure(g);
            Rectangle bounds = new Rectangle(
                0,
                0,
                CARD_SURFACE_WIDTH - 1,
                CARD_SURFACE_HEIGHT - 1);
            RoundRectangle2D card = new RoundRectangle2D.Double(
                bounds.x + 4,
                bounds.y + 4,
                bounds.width - 8,
                bounds.height - 8,
                48,
                48);
            g.setPaint(new GradientPaint(
                bounds.x,
                bounds.y,
                new Color(39, 39, 36),
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                new Color(15, 16, 17)));
            g.fill(card);
            g.setColor(new Color(163, 117, 49));
            g.setStroke(new BasicStroke(12f));
            g.draw(card);

            int inset = 52;
            g.setColor(new Color(235, 195, 103, 105));
            g.setStroke(new BasicStroke(5f));
            g.drawRoundRect(
                inset,
                inset,
                bounds.width - inset * 2,
                bounds.height - inset * 2,
                38,
                38);

            drawCornerOrnaments(g, new Rectangle(42, 42, 576, 936),
                new Color(166, 119, 48));
            drawShieldStar(
                g,
                CARD_SURFACE_WIDTH / 2,
                CARD_SURFACE_HEIGHT * 43 / 100,
                285,
                new Color(38, 38, 35),
                new Color(169, 121, 50),
                new Color(238, 194, 91));
            g.setFont(bold(54));
            g.setColor(new Color(204, 155, 69));
            drawCentred(g, "CARD", CARD_SURFACE_WIDTH / 2, 730);
            drawCentred(g, "LOCKED", CARD_SURFACE_WIDTH / 2, 792);
        }
        finally
        {
            g.dispose();
        }
        cardBackSurface = image;
        return image;
    }

    private void drawCardFront(
        Graphics2D g,
        Rectangle bounds,
        PackCardResult result)
    {
        BufferedImage surface = cardFrontSurface(result);
        drawDisplaySurface(
            g,
            resizedSurface(
                "card-front:" + result.getCardId(),
                surface,
                bounds,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                false),
            bounds);
        if (result.isFoil())
        {
            drawFoilSurfaceOverlay(
                g,
                bounds,
                System.currentTimeMillis(),
                result.getCardId().hashCode());
        }
    }

    public void prewarmCardFronts(PackPresentationSnapshot snapshot)
    {
        Objects.requireNonNull(snapshot, "snapshot");
        for (PackCardResult result : snapshot.getCardResults())
        {
            cardFrontSurface(result);
        }
    }

    private BufferedImage cardFrontSurface(PackCardResult result)
    {
        BufferedImage cached = cardFrontSurfaceCache.get(result.getCardId());
        if (cached != null)
        {
            return cached;
        }
        CardDefinition card = catalogue.requireCard(result.getCardId());
        BufferedImage image = new BufferedImage(
            FRONT_CACHE_WIDTH,
            FRONT_CACHE_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            configure(graphics);
            Rectangle bounds = new Rectangle(
                0,
                0,
                FRONT_CACHE_WIDTH,
                FRONT_CACHE_HEIGHT);
            BufferedImage frameAsset = frameAssets.get(card.getRarity());
            if (frameAsset != null)
            {
                drawDisplaySurface(
                    graphics,
                    resizedSurface(
                        "front-frame:" + card.getRarity().name().toLowerCase(Locale.ROOT),
                        frameAsset,
                        bounds,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                        true),
                    bounds);
            }
            else
            {
                drawFallbackFrame(
                    graphics,
                    bounds,
                    rarityColor(card.getRarity()));
            }
            paintCardForeground(graphics, bounds, card);
        }
        finally
        {
            graphics.dispose();
        }
        cardFrontSurfaceCache.put(result.getCardId(), image);
        return image;
    }

    private void drawFoilSurfaceOverlay(
        Graphics2D g,
        Rectangle bounds,
        long animationTimeMillis,
        long seed)
    {
        Graphics2D fx = (Graphics2D) g.create();
        try
        {
            int inset = Math.max(2, bounds.width / 60);
            RoundRectangle2D clip = new RoundRectangle2D.Double(
                bounds.x + inset,
                bounds.y + inset,
                bounds.width - inset * 2.0,
                bounds.height - inset * 2.0,
                Math.max(12, bounds.width / 13),
                Math.max(12, bounds.width / 13));
            fx.clip(clip);
            double time = animationTimeMillis / 1000.0;

            Graphics2D spectrum = (Graphics2D) fx.create();
            try
            {
                spectrum.rotate(
                    -0.34,
                    bounds.getCenterX(),
                    bounds.getCenterY());
                int band = Math.max(12, bounds.width / 6);
                int travel = (int) Math.round((time * 58.0) % (band * 7));
                spectrum.setComposite(AlphaComposite.SrcOver.derive(0.48f));
                for (int x = bounds.x - bounds.height - band * 8 + travel;
                     x < bounds.x + bounds.width + bounds.height;
                     x += band)
                {
                    float hue = Math.floorMod(x / Math.max(1, band), 14) / 14.0f;
                    Color color = Color.getHSBColor(hue, 0.75f, 1.0f);
                    spectrum.setColor(new Color(
                        color.getRed(),
                        color.getGreen(),
                        color.getBlue(),
                        108));
                    spectrum.fillRect(
                        x,
                        bounds.y - bounds.height,
                        band + 2,
                        bounds.height * 3);
                }
            }
            finally
            {
                spectrum.dispose();
            }

            int sweep = bounds.x + (int) Math.round(
                ((Math.sin(time * 1.4) + 1.0) * 0.5)
                    * (bounds.width + bounds.width * 0.9))
                - bounds.width / 2;
            fx.setComposite(AlphaComposite.SrcOver.derive(0.62f));
            fx.setPaint(new GradientPaint(
                sweep - bounds.width / 3f,
                bounds.y,
                new Color(255, 255, 255, 0),
                sweep + bounds.width / 3f,
                bounds.y + bounds.height,
                new Color(255, 255, 244, 220),
                true));
            fx.fill(clip);

            Random random = new Random(seed ^ animationTimeMillis / 95L);
            int twinkles = 12;
            fx.setComposite(AlphaComposite.SrcOver.derive(0.96f));
            fx.setColor(new Color(255, 255, 244));
            for (int index = 0; index < twinkles; index++)
            {
                int x = bounds.x + random.nextInt(Math.max(1, bounds.width));
                int y = bounds.y + random.nextInt(Math.max(1, bounds.height));
                int arm = Math.max(2, bounds.width / 38) + random.nextInt(3);
                fx.drawLine(x - arm, y, x + arm, y);
                fx.drawLine(x, y - arm, x, y + arm);
                fx.drawLine(x - arm / 2, y - arm / 2, x + arm / 2, y + arm / 2);
                fx.drawLine(x - arm / 2, y + arm / 2, x + arm / 2, y - arm / 2);
            }
        }
        finally
        {
            fx.dispose();
        }
    }

    private void drawFallbackFrame(
        Graphics2D g,
        Rectangle bounds,
        Color rarity)
    {
        RoundRectangle2D frame = new RoundRectangle2D.Double(
            bounds.x + Math.max(2, bounds.width / 64),
            bounds.y + Math.max(2, bounds.height / 64),
            bounds.width - Math.max(4, bounds.width / 32),
            bounds.height - Math.max(4, bounds.height / 32),
            Math.max(12, bounds.width / 14),
            Math.max(12, bounds.width / 14));
        g.setPaint(new GradientPaint(
            bounds.x,
            bounds.y,
            new Color(55, 49, 47),
            bounds.x,
            bounds.y + bounds.height,
            new Color(21, 23, 29)));
        g.fill(frame);
        g.setColor(rarity);
        g.setStroke(new BasicStroke(Math.max(2f, bounds.width / 18f)));
        g.draw(frame);
    }

    private void paintCardForeground(
        Graphics2D g,
        Rectangle bounds,
        CardDefinition card)
    {
        Rectangle nameBox = scaleRect(bounds, 70, 54, 884, 128);
        Font titleFont = fitFont(
            g,
            card.getDisplayName(),
            boldFont,
            Math.max(13, bounds.height / 15),
            Math.max(9, bounds.height / 28),
            nameBox.width - Math.max(20, bounds.width / 10));
        g.setFont(titleFont);
        drawStyledTitle(g, card.getDisplayName(), nameBox, card.getRarity());

        Rectangle artBox = inset(
            scaleRect(bounds, 69, 218, 886, 690),
            Math.max(4, bounds.width / 32),
            Math.max(4, bounds.height / 48));
        drawArtwork(g, card, artBox);

        Rectangle examineBox = inset(
            scaleRect(bounds, 69, 1024, 886, 378),
            Math.max(6, bounds.width / 24),
            Math.max(6, bounds.height / 42));
        drawExamineText(g, CatalogueTextQuality.cardDisplayText(card), examineBox);
    }

    private void drawStyledTitle(
        Graphics2D g,
        String text,
        Rectangle bounds,
        Rarity rarity)
    {
        Color accent = blend(new Color(250, 244, 226), rarityColor(rarity), 0.20);
        g.setColor(new Color(28, 18, 11, 120));
        drawCentredInBox(g, text, translate(bounds, 0, 2));
        g.setColor(new Color(255, 247, 214, 90));
        drawCentredInBox(g, text, translate(bounds, 0, -1));
        g.setColor(accent);
        drawCentredInBox(g, text, bounds);
    }

    private void drawArtwork(
        Graphics2D g,
        CardDefinition card,
        Rectangle artBox)
    {
        CardArtworkProvider.Artwork artwork = artworkProvider.getArtwork(card);
        if (artwork != null && artwork.getImage() != null)
        {
            drawCentredImage(g, artwork.getImage(), artBox, artwork.isPixelArt());
            return;
        }
        drawInitials(
            g,
            card.getDisplayName(),
            artBox,
            artBox.y,
            artBox.height);
    }

    private void drawCentredImage(
        Graphics2D g,
        BufferedImage source,
        Rectangle bounds,
        boolean pixelArt)
    {
        if (source == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }
        BufferedImage trimmed = trimTransparent(source);
        double scale = Math.min(
            bounds.width / (double) Math.max(1, trimmed.getWidth()),
            bounds.height / (double) Math.max(1, trimmed.getHeight()));
        scale *= 0.90;
        int width = Math.max(1, (int) Math.round(trimmed.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(trimmed.getHeight() * scale));
        int x = bounds.x + (bounds.width - width) / 2;
        int y = bounds.y + (bounds.height - height) / 2;
        Object previousInterpolation = g.getRenderingHint(
            RenderingHints.KEY_INTERPOLATION);
        Object previousAntialias = g.getRenderingHint(
            RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            pixelArt
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        if (pixelArt)
        {
            g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
        }
        g.drawImage(trimmed, x, y, width, height, null);
        if (previousInterpolation != null)
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previousInterpolation);
        }
        if (previousAntialias != null)
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousAntialias);
        }
    }

    private BufferedImage resizedSurface(
        String keyPrefix,
        BufferedImage source,
        Rectangle bounds)
    {
        return resizedSurface(
            keyPrefix,
            source,
            bounds,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            false);
    }

    private BufferedImage resizedSurface(
        String keyPrefix,
        BufferedImage source,
        Rectangle bounds,
        Object interpolation,
        boolean sharpen)
    {
        int width = Math.max(1, bounds.width);
        int height = Math.max(1, bounds.height);
        String key = keyPrefix + ':' + width + 'x' + height + ':' + sharpen;
        BufferedImage cached = displaySurfaceCache.get(key);
        if (cached != null)
        {
            return cached;
        }

        BufferedImage image = new BufferedImage(
            width,
            height,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            configure(g);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.drawImage(source, 0, 0, width, height, null);
        }
        finally
        {
            g.dispose();
        }
        if (sharpen)
        {
            image = sharpen(image);
        }
        displaySurfaceCache.put(key, image);
        return image;
    }
    private void drawDisplaySurface(
        Graphics2D g,
        BufferedImage surface,
        Rectangle bounds)
    {
        if (surface.getWidth() == bounds.width
            && surface.getHeight() == bounds.height)
        {
            g.drawImage(surface, bounds.x, bounds.y, null);
            return;
        }
        Object previousInterpolation = g.getRenderingHint(
            RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(
            surface,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            null);
        if (previousInterpolation != null)
        {
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                previousInterpolation);
        }
    }

    private static Rectangle scaleRect(
        Rectangle outer,
        int x,
        int y,
        int width,
        int height)
    {
        return new Rectangle(
            outer.x + (int) Math.round(x * outer.width / (double) CARD_SURFACE_WIDTH),
            outer.y + (int) Math.round(y * outer.height / (double) CARD_SURFACE_HEIGHT),
            Math.max(1, (int) Math.round(width * outer.width / (double) CARD_SURFACE_WIDTH)),
            Math.max(1, (int) Math.round(height * outer.height / (double) CARD_SURFACE_HEIGHT)));
    }

    private static Rectangle inset(Rectangle source, int dx, int dy)
    {
        return new Rectangle(
            source.x + dx,
            source.y + dy,
            Math.max(1, source.width - dx * 2),
            Math.max(1, source.height - dy * 2));
    }

    private static BufferedImage sharpen(BufferedImage source)
    {
        float[] kernel = new float[]{
            0.0f, -0.18f, 0.0f,
            -0.18f, 1.72f, -0.18f,
            0.0f, -0.18f, 0.0f};
        return new ConvolveOp(
            new Kernel(3, 3, kernel),
            ConvolveOp.EDGE_NO_OP,
            null).filter(source, null);
    }

    private static BufferedImage trimTransparent(BufferedImage source)
    {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++)
        {
            for (int x = 0; x < source.getWidth(); x++)
            {
                if (((source.getRGB(x, y) >>> 24) & 0xff) > 8)
                {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY)
        {
            return source;
        }
        return source.getSubimage(
            minX,
            minY,
            maxX - minX + 1,
            maxY - minY + 1);
    }

    private void drawTextBox(
        Graphics2D g,
        Rectangle bounds,
        Color fill,
        Color border)
    {
        g.setColor(fill);
        g.fillRoundRect(
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            7,
            7);
        g.setColor(border);
        g.setStroke(new BasicStroke(Math.max(1.0f, bounds.width / 230.0f)));
        g.drawRoundRect(
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            7,
            7);
    }

    private static void drawCornerOrnaments(
        Graphics2D g,
        Rectangle bounds,
        Color color)
    {
        int arm = Math.max(18, Math.min(bounds.width, bounds.height) / 12);
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(2f, arm / 12f)));
        g.drawLine(bounds.x, bounds.y + arm, bounds.x, bounds.y);
        g.drawLine(bounds.x, bounds.y, bounds.x + arm, bounds.y);
        g.drawLine(bounds.x + bounds.width - arm, bounds.y,
            bounds.x + bounds.width, bounds.y);
        g.drawLine(bounds.x + bounds.width, bounds.y,
            bounds.x + bounds.width, bounds.y + arm);
        g.drawLine(bounds.x, bounds.y + bounds.height - arm,
            bounds.x, bounds.y + bounds.height);
        g.drawLine(bounds.x, bounds.y + bounds.height,
            bounds.x + arm, bounds.y + bounds.height);
        g.drawLine(bounds.x + bounds.width - arm,
            bounds.y + bounds.height,
            bounds.x + bounds.width,
            bounds.y + bounds.height);
        g.drawLine(bounds.x + bounds.width,
            bounds.y + bounds.height - arm,
            bounds.x + bounds.width,
            bounds.y + bounds.height);
    }

    private static void drawShieldStar(
        Graphics2D g,
        int centerX,
        int centerY,
        int size,
        Color shieldFill,
        Color shieldBorder,
        Color starColor)
    {
        int half = size / 2;
        Polygon shield = new Polygon(
            new int[]{
                centerX - half,
                centerX + half,
                centerX + half * 4 / 5,
                centerX,
                centerX - half * 4 / 5
            },
            new int[]{
                centerY - half * 3 / 4,
                centerY - half * 3 / 4,
                centerY + half / 3,
                centerY + half,
                centerY + half / 3
            },
            5);
        g.setColor(shieldFill);
        g.fillPolygon(shield);
        g.setColor(shieldBorder);
        g.setStroke(new BasicStroke(Math.max(5f, size / 35f)));
        g.drawPolygon(shield);

        int starRadius = size * 22 / 100;
        Polygon star = new Polygon(
            new int[]{
                centerX,
                centerX + starRadius / 3,
                centerX + starRadius,
                centerX + starRadius / 3,
                centerX,
                centerX - starRadius / 3,
                centerX - starRadius,
                centerX - starRadius / 3
            },
            new int[]{
                centerY - starRadius,
                centerY - starRadius / 3,
                centerY,
                centerY + starRadius / 3,
                centerY + starRadius,
                centerY + starRadius / 3,
                centerY,
                centerY - starRadius / 3
            },
            8);
        g.setColor(starColor);
        g.fillPolygon(star);
        g.setColor(new Color(74, 45, 16, 180));
        g.setStroke(new BasicStroke(Math.max(2f, size / 70f)));
        g.drawPolygon(star);
    }

    private void drawInitials(
        Graphics2D g,
        String name,
        Rectangle bounds,
        int artY,
        int artH)
    {
        String[] words = name.trim().split("\\s+");
        String initials;
        if (words.length == 1)
        {
            String value = words[0].toUpperCase(Locale.ROOT);
            initials = value.substring(0, Math.min(2, value.length()));
        }
        else
        {
            initials = ("" + words[0].charAt(0)
                + words[words.length - 1].charAt(0))
                .toUpperCase(Locale.ROOT);
        }
        g.setFont(bold(Math.max(14, bounds.width / 4)));
        g.setColor(PALE_GOLD);
        drawCentred(
            g,
            initials,
            (int) bounds.getCenterX(),
            artY + artH / 2 + g.getFontMetrics().getAscent() / 3);
    }

    private void drawOutcomeBelow(
        Graphics2D g,
        Rectangle bounds,
        PackCardResult result,
        boolean emphasized)
    {
        Color color = result.isDuplicate() ? DUPLICATE : SUCCESS;
        if (emphasized)
        {
            g.setColor(new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                42));
            g.fillRoundRect(
                bounds.x - 3,
                bounds.y - 2,
                bounds.width + 6,
                bounds.height + 4,
                10,
                10);
        }
        g.setFont(bold(Math.max(9, bounds.height * 38 / 100)));
        g.setColor(color);
        String baseText = result.isDuplicate()
            ? "+" + result.getShardsAwarded() + " SHARDS"
            : "NEW UNLOCK";
        String text = result.isFoil()
            ? "FOIL  •  " + baseText
            : baseText;
        if (result.isFoil())
        {
            color = new Color(226, 166, 255);
        }
        drawCentred(
            g,
            text,
            (int) bounds.getCenterX(),
            (int) bounds.getCenterY() + g.getFontMetrics().getAscent() / 3);
    }

    private void drawUnrevealedHint(
        Graphics2D g,
        Rectangle bounds,
        boolean selectable,
        boolean requested)
    {
        if (!requested)
        {
            return;
        }
        int dot = Math.max(2, bounds.height / 8);
        int gap = dot * 2;
        int centerX = (int) bounds.getCenterX();
        int centerY = (int) bounds.getCenterY();
        g.setColor(MUTED);
        for (int index = -1; index <= 1; index++)
        {
            g.fillOval(
                centerX + index * gap - dot / 2,
                centerY - dot / 2,
                dot,
                dot);
        }
    }

    private void drawPersistentRarityAura(
        Graphics2D g,
        Rectangle bounds,
        PackCardResult result,
        long animationTimeMillis,
        int position,
        boolean reducedMotion)
    {
        Rarity rarity = catalogue.requireCard(result.getCardId()).getRarity();
        if (rarity.ordinal() < Rarity.RARE.ordinal() && !result.isFoil())
        {
            return;
        }
        int tier = Math.max(
            1,
            rarity.ordinal() - Rarity.RARE.ordinal() + 1)
            + (result.isFoil() ? 2 : 0);
        double pulse = reducedMotion
            ? 0.5
            : 0.5 + 0.5 * Math.sin(animationTimeMillis / 520.0 + position * 0.7);
        Color color = rarityColor(rarity);
        for (int ring = 3; ring >= 1; ring--)
        {
            int spread = 3 + ring * 3;
            int alpha = (int) Math.round(8 + tier * 3 + pulse * (6 + tier * 2));
            g.setColor(withAlpha(color, Math.min(38, alpha)));
            g.fillRoundRect(
                bounds.x - spread,
                bounds.y - spread,
                bounds.width + spread * 2,
                bounds.height + spread * 2,
                18 + spread,
                18 + spread);
        }
    }

    private void drawPersistentRarityTwinkles(
        Graphics2D g,
        Rectangle bounds,
        PackCardResult result,
        long animationTimeMillis,
        int position,
        boolean reducedMotion)
    {
        Rarity rarity = catalogue.requireCard(result.getCardId()).getRarity();
        if (rarity.ordinal() < Rarity.RARE.ordinal() && !result.isFoil())
        {
            return;
        }
        int tier = Math.max(
            1,
            rarity.ordinal() - Rarity.RARE.ordinal() + 1)
            + (result.isFoil() ? 3 : 0);
        Color color = rarityColor(rarity);
        long timeBucket = reducedMotion ? 0L : animationTimeMillis / 180L;
        Random random = new Random(
            result.getCardId().hashCode()
                ^ ((long) position << 32)
                ^ timeBucket);
        int count = 1 + tier * 2 + (result.isFoil() ? 8 : 0);
        for (int index = 0; index < count; index++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = Math.max(bounds.width, bounds.height)
                * (0.35 + random.nextDouble() * 0.14);
            int x = (int) Math.round(bounds.getCenterX() + Math.cos(angle) * radius);
            int y = (int) Math.round(bounds.getCenterY() + Math.sin(angle) * radius);
            int arm = 2 + random.nextInt(Math.max(1, tier + 1));
            int alpha = 28 + tier * 11;
            g.setColor(withAlpha(color, Math.min(105, alpha)));
            g.drawLine(x - arm, y, x + arm, y);
            g.drawLine(x, y - arm, x, y + arm);
        }
    }

    private void drawFoilRevealBurst(
        Graphics2D g,
        Rectangle bounds,
        long animationTimeMillis,
        long seed,
        double revealProgress)
    {
        double value = clamp(revealProgress);
        double charge = clamp(value / 0.79);
        double impact = clamp((value - 0.79) / 0.12);
        double settle = clamp((value - 0.91) / 0.09);
        double chargePulse = Math.sin(Math.PI * charge);
        double impactPulse = Math.sin(Math.PI * impact);
        int centerX = (int) bounds.getCenterX();
        int centerY = (int) bounds.getCenterY();
        int baseRadius = Math.max(bounds.width, bounds.height) / 2;
        Composite previous = g.getComposite();
        Random random = new Random(seed);

        if (charge > 0.0 && impact < 1.0)
        {
            g.setStroke(new BasicStroke(
                2.0f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            for (int arc = 0; arc < 4; arc++)
            {
                int radius = (int) Math.round(
                    baseRadius * (0.62 + arc * 0.105 + charge * 0.08));
                int angle = (int) Math.round(
                    animationTimeMillis * (arc % 2 == 0 ? 0.16 : -0.13)
                        + arc * 71.0);
                Color arcColor = Color.getHSBColor(
                    (float) ((arc * 0.19 + animationTimeMillis / 2600.0) % 1.0),
                    0.48f,
                    1.0f);
                g.setComposite(AlphaComposite.SrcOver.derive(
                    (float) Math.min(0.62, 0.12 + chargePulse * 0.50)));
                g.setColor(arcColor);
                g.drawArc(
                    centerX - radius,
                    centerY - radius,
                    radius * 2,
                    radius * 2,
                    angle,
                    74 + arc * 11);
            }

            int inwardStreaks = 26;
            for (int index = 0; index < inwardStreaks; index++)
            {
                double angle = index * Math.PI * 2.0 / inwardStreaks
                    + random.nextDouble() * 0.10;
                double outer = baseRadius * (1.55 - charge * 0.60)
                    + random.nextDouble() * 22.0;
                double inner = Math.max(
                    baseRadius * 0.43,
                    outer - 22.0 - charge * 45.0);
                int x1 = (int) Math.round(centerX + Math.cos(angle) * outer);
                int y1 = (int) Math.round(centerY + Math.sin(angle) * outer);
                int x2 = (int) Math.round(centerX + Math.cos(angle) * inner);
                int y2 = (int) Math.round(centerY + Math.sin(angle) * inner);
                Color streak = Color.getHSBColor(
                    (float) ((index / (double) inwardStreaks
                        + animationTimeMillis / 3100.0) % 1.0),
                    0.42f,
                    1.0f);
                g.setComposite(AlphaComposite.SrcOver.derive(
                    (float) Math.min(0.68, 0.10 + chargePulse * 0.58)));
                g.setColor(streak);
                g.drawLine(x1, y1, x2, y2);
            }
        }

        if (impact > 0.0)
        {
            int flashRadius = (int) Math.round(
                baseRadius * (0.34 + easeOutCubic(impact) * 1.05));
            g.setComposite(AlphaComposite.SrcOver.derive(
                (float) Math.min(0.78, impactPulse * 0.78)));
            g.setColor(new Color(255, 252, 255));
            g.fillOval(
                centerX - flashRadius,
                centerY - flashRadius,
                flashRadius * 2,
                flashRadius * 2);

            g.setStroke(new BasicStroke(
                2.8f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            for (int ring = 0; ring < 5; ring++)
            {
                double local = clamp(impact * 1.28 - ring * 0.105);
                int radius = (int) Math.round(
                    baseRadius * (0.48 + local * (1.25 + ring * 0.12)));
                float alpha = (float) Math.max(
                    0.03,
                    Math.min(0.82, (1.0 - local) * (0.76 - ring * 0.08)));
                Color ringColor = Color.getHSBColor(
                    (float) ((ring * 0.17 + animationTimeMillis / 2200.0) % 1.0),
                    0.54f,
                    1.0f);
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g.setColor(ringColor);
                g.drawOval(
                    centerX - radius,
                    centerY - radius,
                    radius * 2,
                    radius * 2);
            }

            int rays = 42;
            g.setStroke(new BasicStroke(
                1.8f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            for (int index = 0; index < rays; index++)
            {
                double angle = index * Math.PI * 2.0 / rays
                    + random.nextDouble() * 0.08;
                double energy = 0.46 + random.nextDouble() * 0.54;
                double start = baseRadius * (0.34 + impact * 0.38);
                double length = 22.0 + impact * energy * 118.0;
                int x1 = (int) Math.round(centerX + Math.cos(angle) * start);
                int y1 = (int) Math.round(centerY + Math.sin(angle) * start);
                int x2 = (int) Math.round(
                    centerX + Math.cos(angle) * (start + length));
                int y2 = (int) Math.round(
                    centerY + Math.sin(angle) * (start + length));
                Color ray = Color.getHSBColor(
                    (float) ((index / (double) rays
                        + animationTimeMillis / 2800.0) % 1.0),
                    0.57f,
                    1.0f);
                g.setComposite(AlphaComposite.SrcOver.derive(
                    (float) Math.min(0.92, impactPulse * energy)));
                g.setColor(ray);
                g.drawLine(x1, y1, x2, y2);
            }

            int fragments = 58;
            for (int index = 0; index < fragments; index++)
            {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double speed = 0.38 + random.nextDouble() * 0.92;
                double distance = baseRadius * 0.30
                    + easeOutCubic(impact) * speed * baseRadius * 2.15;
                int x = (int) Math.round(centerX + Math.cos(angle) * distance);
                int y = (int) Math.round(centerY + Math.sin(angle) * distance);
                int size = 2 + random.nextInt(5);
                Color fragment = Color.getHSBColor(
                    (float) ((random.nextDouble()
                        + animationTimeMillis / 3000.0) % 1.0),
                    0.48f,
                    1.0f);
                float alpha = (float) Math.max(
                    0.10,
                    Math.min(0.92, (1.0 - impact * 0.30) * speed));
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g.setColor(fragment);
                g.fillOval(x - size / 2, y - size / 2, size, size);
            }
        }

        if (settle > 0.0)
        {
            int stars = 16;
            for (int index = 0; index < stars; index++)
            {
                double phase = index * 2.399963229728653
                    + animationTimeMillis / 650.0;
                double radius = baseRadius * (0.56 + (index % 4) * 0.12);
                int x = (int) Math.round(centerX + Math.cos(phase) * radius);
                int y = (int) Math.round(centerY + Math.sin(phase * 1.17) * radius);
                int arm = 3 + index % 4;
                float alpha = (float) Math.min(
                    0.88,
                    (1.0 - settle * 0.34)
                        * (0.42 + 0.42 * Math.sin(phase * 1.9) * Math.sin(phase * 1.9)));
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g.setColor(new Color(255, 255, 246));
                g.drawLine(x - arm, y, x + arm, y);
                g.drawLine(x, y - arm, x, y + arm);
            }
        }
        g.setComposite(previous);
    }

    private void drawRarityEffect(
        Graphics2D g,
        Rectangle bounds,
        PackCardResult result,
        long animationTimeMillis,
        int position,
        UUID openingId,
        double revealProgress)
    {
        Rarity rarity = catalogue.requireCard(result.getCardId()).getRarity();
        Color color = rarityColor(rarity);
        int tier = rarity.ordinal() + 1;
        long seed = (openingId == null ? 0L : openingId.getLeastSignificantBits())
            ^ ((long) position << 32)
            ^ result.getCardId().hashCode();
        Random random = new Random(seed);
        double pulse = 0.55 + 0.45 * Math.sin(animationTimeMillis / 125.0);
        double maxRadius = Math.max(bounds.width, bounds.height) * (0.42 + tier * 0.03);

        Graphics2D fx = (Graphics2D) g.create();
        try
        {
            if (result.isFoil())
            {
                drawFoilRevealBurst(
                    fx,
                    bounds,
                    animationTimeMillis,
                    seed,
                    revealProgress);
            }
            fx.setStroke(new BasicStroke(1.0f + tier * 0.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int sparks = 8 + tier * 4;
            for (int i = 0; i < sparks; i++)
            {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double startRadius = Math.max(bounds.width, bounds.height) * 0.34
                    + random.nextDouble() * 12.0;
                double length = 8.0 + tier * 2.8 + pulse * 10.0 + random.nextDouble() * 8.0;
                int x1 = (int) Math.round(bounds.getCenterX() + Math.cos(angle) * startRadius);
                int y1 = (int) Math.round(bounds.getCenterY() + Math.sin(angle) * startRadius);
                int x2 = (int) Math.round(bounds.getCenterX() + Math.cos(angle) * (startRadius + length));
                int y2 = (int) Math.round(bounds.getCenterY() + Math.sin(angle) * (startRadius + length));
                fx.setColor(withAlpha(color, 38 + tier * 11));
                fx.drawLine(x1, y1, x2, y2);
            }

            int sparkles = 14 + tier * 5;
            for (int i = 0; i < sparkles; i++)
            {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double travel = ((animationTimeMillis % 820L) / 820.0 + random.nextDouble()) % 1.0;
                double radius = 10.0 + travel * maxRadius;
                int x = (int) Math.round(bounds.getCenterX() + Math.cos(angle) * radius);
                int y = (int) Math.round(bounds.getCenterY() + Math.sin(angle) * radius);
                int size = Math.max(2, (int) Math.round((1.0 - travel) * (3.8 + tier * 0.55)));
                int alpha = (int) Math.round((1.0 - travel) * (78 + tier * 14));
                fx.setColor(withAlpha(color, alpha));
                fx.fillOval(x - size / 2, y - size / 2, size, size);
                int arm = Math.max(2, size + (tier >= Rarity.RARE.ordinal() + 1 ? 1 : 0));
                fx.drawLine(x - arm, y, x + arm, y);
                fx.drawLine(x, y - arm, x, y + arm);
            }

            int burstStars = Math.max(2, tier / 2 + 1);
            for (int i = 0; i < burstStars; i++)
            {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double radius = Math.max(bounds.width, bounds.height) * (0.38 + random.nextDouble() * 0.18);
                int x = (int) Math.round(bounds.getCenterX() + Math.cos(angle) * radius);
                int y = (int) Math.round(bounds.getCenterY() + Math.sin(angle) * radius);
                int arm = (int) Math.round(4 + tier * 0.7 + pulse * 2.5);
                fx.setColor(withAlpha(color, 70 + tier * 12));
                fx.drawLine(x - arm, y, x + arm, y);
                fx.drawLine(x, y - arm, x, y + arm);
                fx.drawLine(x - arm + 1, y - arm + 1, x + arm - 1, y + arm - 1);
                fx.drawLine(x - arm + 1, y + arm - 1, x + arm - 1, y - arm + 1);
            }
        }
        finally
        {
            fx.dispose();
        }
    }

    private void drawSummary(


        Graphics2D g,
        Layout layout,
        PackPresentationSnapshot snapshot)
    {
        int duplicates = snapshot.getDuplicateCount();
        long shards = snapshot.getDuplicateShards();
        int newCards = snapshot.getNewCardCount();
        Rectangle panel = layout.summaryPanel;
        g.setColor(PANEL);
        g.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 14, 14);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(panel.x, panel.y, panel.width, panel.height, 14, 14);
        g.setFont(bold(Math.max(10, layout.subtitleSize - 1)));
        g.setColor(Color.WHITE);
        drawCentred(
            g,
            newCards + " NEW   |   " + duplicates + " DUPLICATE"
                + (duplicates == 1 ? "" : "S")
                + "   |   +" + shards + " SHARDS",
            layout.centerX,
            (int) panel.getCenterY() + g.getFontMetrics().getAscent() / 3);
    }

    private void drawPackDiagnostics(
        Graphics2D g,
        Layout layout,
        PackPresentationSnapshot snapshot)
    {
        int width = Math.max(120, Math.min(
            Math.max(120, layout.viewport.width - 16),
            Math.min(320, Math.max(180, layout.viewport.width / 3))));
        int lineHeight = Math.max(13, layout.subtitleSize + 3);
        int height = lineHeight * 6 + 16;
        int x = layout.viewport.x + 8;
        int y = layout.viewport.y + layout.viewport.height - height - 8;
        y = Math.max(layout.viewport.y + 8, y);
        g.setColor(new Color(8, 10, 15, 220));
        g.fillRoundRect(x, y, width, height, 10, 10);
        g.setColor(new Color(194, 151, 67, 205));
        g.drawRoundRect(x, y, width, height, 10, 10);
        g.setFont(bold(Math.max(10, layout.subtitleSize)));
        g.setColor(PALE_GOLD);
        int baseline = y + 15;
        g.drawString("PACK SESSION", x + 9, baseline);
        g.setFont(small(Math.max(9, layout.subtitleSize - 1)));
        g.setColor(Color.WHITE);
        baseline += lineHeight;
        g.drawString(
            "State: " + snapshot.getState().name(),
            x + 9,
            baseline);
        baseline += lineHeight;
        g.drawString(
            "Pack: " + snapshot.getPackId().orElse("none"),
            x + 9,
            baseline);
        baseline += lineHeight;
        g.drawString(
            "Opening: " + snapshot.getOpeningId()
                .map(Object::toString)
                .orElse("none"),
            x + 9,
            baseline);
        baseline += lineHeight;
        g.drawString(
            "Revealed: " + snapshot.getRevealedPositions()
                + " requested: " + snapshot.getRequestedPositions(),
            x + 9,
            baseline);
        baseline += lineHeight;
        g.drawString(
            "Committed complete: " + snapshot.isOpeningComplete()
                + " active flips: " + snapshot.hasActiveFlips(),
            x + 9,
            baseline);
    }

    private void drawButton(
        Graphics2D g,
        Rectangle bounds,
        String label,
        boolean hovered,
        boolean primary)
    {
        g.setColor(primary
            ? (hovered ? PALE_GOLD : GOLD)
            : (hovered ? new Color(75, 78, 91) : new Color(45, 48, 59)));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 11, 11);
        g.setColor(primary ? PALE_GOLD : new Color(151, 154, 165));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 11, 11);
        g.setFont(bold(Math.max(9, bounds.height * 38 / 100)));
        g.setColor(primary ? INK : Color.WHITE);
        drawCentred(
            g,
            label,
            (int) bounds.getCenterX(),
            (int) bounds.getCenterY() + g.getFontMetrics().getAscent() / 3);
    }

    private Font regular(int size)
    {
        return regularFont.deriveFont(Font.PLAIN, (float) size);
    }

    private Font bold(int size)
    {
        return boldFont.deriveFont(Font.BOLD, (float) size);
    }

    private Font small(int size)
    {
        return smallFont.deriveFont(Font.PLAIN, (float) size);
    }

    private static Font fitFont(
        Graphics2D g,
        String text,
        Font baseFont,
        int maximumSize,
        int minimumSize,
        int maximumWidth)
    {
        for (int size = maximumSize; size >= minimumSize; size--)
        {
            Font candidate = baseFont.deriveFont(Font.BOLD, (float) size);
            if (g.getFontMetrics(candidate).stringWidth(text) <= maximumWidth)
            {
                return candidate;
            }
        }
        return baseFont.deriveFont(Font.BOLD, (float) minimumSize);
    }

    private void drawExamineText(
        Graphics2D g,
        String text,
        Rectangle bounds)
    {
        String value = text == null ? "" : text.trim();
        int maxWidth = Math.max(24, bounds.width - Math.max(12, bounds.width / 12));
        int maxSize = Math.max(14, bounds.height / 4);
        int minSize = Math.max(10, bounds.height / 7);
        for (int size = maxSize; size >= minSize; size--)
        {
            g.setFont(regular(size));
            List<String> lines = wrap(g.getFontMetrics(), value, maxWidth, 3);
            int totalHeight = lines.size() * g.getFontMetrics().getHeight();
            if (totalHeight <= bounds.height - Math.max(8, bounds.height / 10))
            {
                g.setColor(new Color(49, 39, 29));
                drawWrappedCentredInBox(g, value, bounds, maxWidth, 3);
                return;
            }
        }
        g.setFont(regular(minSize));
        g.setColor(new Color(49, 39, 29));
        drawWrappedCentredInBox(g, value, bounds, maxWidth, 3);
    }

    private double animationFrameSeconds(long animationTimeMillis)
    {
        if (previousAnimationTimeMillis < 0L)
        {
            previousAnimationTimeMillis = animationTimeMillis;
            return 1.0 / 60.0;
        }
        long elapsed = animationTimeMillis - previousAnimationTimeMillis;
        previousAnimationTimeMillis = animationTimeMillis;
        return Math.max(0.0, Math.min(0.05, elapsed / 1000.0));
    }

    private static void configure(Graphics2D g)
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void withAlpha(
        Graphics2D g,
        float alpha,
        Runnable renderer)
    {
        Composite original = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(
            AlphaComposite.SRC_OVER,
            (float) clamp(alpha)));
        try
        {
            renderer.run();
        }
        finally
        {
            g.setComposite(original);
        }
    }

    private static void drawCentred(
        Graphics2D g,
        String text,
        int centerX,
        int baselineY)
    {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }

    private static void drawCentredInBox(
        Graphics2D g,
        String text,
        Rectangle bounds)
    {
        FontMetrics metrics = g.getFontMetrics();
        int baseline = bounds.y
            + Math.max(
                metrics.getAscent(),
                (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent());
        drawCentred(g, text, (int) bounds.getCenterX(), baseline);
    }

    private static Rectangle translate(Rectangle source, int dx, int dy)
    {
        return new Rectangle(
            source.x + dx,
            source.y + dy,
            source.width,
            source.height);
    }

    private static Color blend(Color base, Color accent, double amount)
    {
        double clamped = clamp(amount);
        return new Color(
            (int) Math.round(base.getRed() * (1.0 - clamped)
                + accent.getRed() * clamped),
            (int) Math.round(base.getGreen() * (1.0 - clamped)
                + accent.getGreen() * clamped),
            (int) Math.round(base.getBlue() * (1.0 - clamped)
                + accent.getBlue() * clamped),
            255);
    }

    private static void drawWrappedCentred(
        Graphics2D g,
        String text,
        int centerX,
        int firstBaseline,
        int maxWidth,
        int maxLines)
    {
        List<String> lines = wrap(g.getFontMetrics(), text, maxWidth, maxLines);
        int lineHeight = g.getFontMetrics().getHeight();
        for (int index = 0; index < lines.size(); index++)
        {
            drawCentred(
                g,
                lines.get(index),
                centerX,
                firstBaseline + index * lineHeight);
        }
    }

    private static void drawWrappedCentredInBox(
        Graphics2D g,
        String text,
        Rectangle bounds,
        int maxWidth,
        int maxLines)
    {
        List<String> lines = wrap(
            g.getFontMetrics(),
            text,
            maxWidth,
            maxLines);
        int lineHeight = g.getFontMetrics().getHeight();
        int totalHeight = lines.size() * lineHeight;
        int firstBaseline = bounds.y
            + Math.max(lineHeight, (bounds.height - totalHeight) / 2
                + g.getFontMetrics().getAscent());
        for (int index = 0; index < lines.size(); index++)
        {
            drawCentred(
                g,
                lines.get(index),
                (int) bounds.getCenterX(),
                firstBaseline + index * lineHeight);
        }
    }

    private static List<String> wrap(
        FontMetrics metrics,
        String text,
        int maxWidth,
        int maxLines)
    {
        if (metrics.stringWidth(text) <= maxWidth)
        {
            return Collections.singletonList(text);
        }
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+"))
        {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth)
            {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0)
            {
                result.add(line.toString());
            }
            line.setLength(0);
            line.append(word);
            if (result.size() == maxLines - 1)
            {
                break;
            }
        }
        if (result.size() < maxLines && line.length() > 0)
        {
            result.add(line.toString());
        }
        return result.isEmpty() ? Collections.singletonList(text) : result;
    }

    private String readable(Enum<?> value)
    {
        String lower = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static Color rarityColor(Rarity rarity)
    {
        switch (rarity)
        {
            case COMMON:
                return new Color(184, 141, 92);
            case UNCOMMON:
                return new Color(116, 196, 112);
            case RARE:
                return new Color(80, 158, 229);
            case EPIC:
                return new Color(174, 104, 224);
            case LEGENDARY:
                return new Color(245, 151, 50);
            case MYTHIC:
                return new Color(232, 72, 91);
            case GODLY:
                return new Color(250, 220, 89);
            default:
                return GOLD;
        }
    }

    private static Color withAlpha(Color color, int alpha)
    {
        return new Color(
            color.getRed(),
            color.getGreen(),
            color.getBlue(),
            Math.max(0, Math.min(255, alpha)));
    }

    private static boolean contains(Rectangle bounds, Point point)
    {
        return point != null && bounds.contains(point);
    }

    private static Rectangle expand(Rectangle source, int amount)
    {
        return new Rectangle(
            source.x - amount,
            source.y - amount,
            source.width + amount * 2,
            source.height + amount * 2);
    }

    private static Rectangle scale(
        Rectangle source,
        double centerX,
        double centerY,
        double scaleX,
        double scaleY)
    {
        int width = Math.max(1, (int) Math.round(source.width * scaleX));
        int height = Math.max(1, (int) Math.round(source.height * scaleY));
        return new Rectangle(
            (int) Math.round(centerX - width / 2.0),
            (int) Math.round(centerY - height / 2.0),
            width,
            height);
    }

    private static int lerp(int start, int end, double progress)
    {
        return (int) Math.round(start + (end - start) * progress);
    }

    private static double easeOutCubic(double value)
    {
        double inverse = 1.0 - clamp(value);
        return 1.0 - inverse * inverse * inverse;
    }

    private static double easeInOutCubic(double value)
    {
        double clamped = clamp(value);
        return clamped < 0.5
            ? 4.0 * clamped * clamped * clamped
            : 1.0 - Math.pow(-2.0 * clamped + 2.0, 3.0) / 2.0;
    }

    private static double easeOutBack(double value)
    {
        double clamped = clamp(value);
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * Math.pow(clamped - 1.0, 3.0)
            + c1 * Math.pow(clamped - 1.0, 2.0);
    }

    private static double clamp(double value)
    {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class Layout
    {
        private final Rectangle viewport;
        private final int centerX;
        private final int titleY;
        private final int subtitleY;
        private final int titleSize;
        private final int subtitleSize;
        private final int headingPanelY;
        private final int headingPanelWidth;
        private final int headingPanelHeight;
        private final Rectangle wrapper;
        private final List<Rectangle> cards;
        private final List<Rectangle> outcomes;
        private final Rectangle skipButton;
        private final Rectangle closeButton;
        private final Rectangle summaryPanel;

        private Layout(
            Rectangle viewport,
            int centerX,
            int titleY,
            int subtitleY,
            int titleSize,
            int subtitleSize,
            int headingPanelY,
            int headingPanelWidth,
            int headingPanelHeight,
            Rectangle wrapper,
            List<Rectangle> cards,
            List<Rectangle> outcomes,
            Rectangle skipButton,
            Rectangle closeButton,
            Rectangle summaryPanel)
        {
            this.viewport = new Rectangle(viewport);
            this.centerX = centerX;
            this.titleY = titleY;
            this.subtitleY = subtitleY;
            this.titleSize = titleSize;
            this.subtitleSize = subtitleSize;
            this.headingPanelY = headingPanelY;
            this.headingPanelWidth = headingPanelWidth;
            this.headingPanelHeight = headingPanelHeight;
            this.wrapper = wrapper;
            this.cards = cards;
            this.outcomes = outcomes;
            this.skipButton = skipButton;
            this.closeButton = closeButton;
            this.summaryPanel = summaryPanel;
        }

        private static Layout create(
            Rectangle viewport,
            double zoomFactor,
            int cardCount)
        {
            int centerX = viewport.x + viewport.width / 2;
            int centerY = viewport.y + viewport.height / 2;
            double viewportScale = Math.max(
                0.42,
                Math.min(
                    1.12,
                    Math.min(
                        viewport.width / 1248.0,
                        viewport.height / 854.0)));
            double zoom = Math.max(0.68, Math.min(1.70, zoomFactor));

            int horizontalGap = Math.max(10, (int) Math.round(34 * viewportScale));
            int rowGap = Math.max(8, (int) Math.round(22 * viewportScale));
            int outcomeGap = Math.max(4, (int) Math.round(10 * viewportScale));
            int outcomeH = Math.max(16, (int) Math.round(32 * viewportScale));
            int idealCardW = Math.max(52, (int) Math.round(214 * viewportScale));
            int maxCardWByWidth = Math.max(52,
                (viewport.width - 24 - horizontalGap * 2) / 3);
            int verticalBudget = Math.max(156, viewport.height - 20);
            int maxCardHByHeight = Math.max(78,
                (verticalBudget - rowGap
                    - 2 * (outcomeGap + outcomeH)) / 2);
            int maxCardWByHeight = Math.max(52,
                (int) Math.floor(maxCardHByHeight / 1.50));
            int cardW = Math.max(52, Math.min(
                idealCardW,
                Math.min(maxCardWByWidth, maxCardWByHeight)));
            int cardH = Math.max(78, (int) Math.round(cardW * 1.50));

            int titleSize = Math.max(10, (int) Math.round(15 * viewportScale));
            int subtitleSize = Math.max(9, (int) Math.round(13 * viewportScale));
            int headingPanelHeight = Math.max(36,
                titleSize + subtitleSize + 13);
            int headingPanelWidth = Math.min(
                Math.max(80, viewport.width - 20),
                Math.max(300, (int) Math.round(720 * viewportScale)));
            int headingPanelY = viewport.y + 6;
            int titleY = headingPanelY + titleSize + 2;
            int subtitleY = headingPanelY + headingPanelHeight - 7;

            if (cardCount == 1)
            {
                int singleW = Math.max(cardW, (int) Math.round(cardW * 1.18));
                int singleH = Math.max(cardH, (int) Math.round(singleW * 1.50));
                Rectangle singleBase = new Rectangle(
                    centerX - singleW / 2,
                    centerY - (singleH + outcomeGap + outcomeH) / 2,
                    singleW,
                    singleH);
                Rectangle outcomeBase = new Rectangle(
                    centerX - singleW / 2,
                    singleBase.y + singleH + outcomeGap,
                    singleW,
                    outcomeH);
                Rectangle singleCard = scaleAround(
                    singleBase,
                    centerX,
                    centerY,
                    zoom);
                Rectangle singleOutcome = scaleAround(
                    outcomeBase,
                    centerX,
                    centerY,
                    zoom);
                int wrapperW = Math.max(130, (int) Math.round(260 * viewportScale));
                int wrapperH = Math.max(195, (int) Math.round(390 * viewportScale));
                Rectangle wrapper = scaleAround(
                    new Rectangle(
                        centerX - wrapperW / 2,
                        centerY - wrapperH / 2,
                        wrapperW,
                        wrapperH),
                    centerX,
                    centerY,
                    zoom);
                return new Layout(
                    viewport,
                    centerX,
                    titleY,
                    subtitleY,
                    titleSize,
                    subtitleSize,
                    headingPanelY,
                    headingPanelWidth,
                    headingPanelHeight,
                    wrapper,
                    Collections.singletonList(singleCard),
                    Collections.singletonList(singleOutcome),
                    new Rectangle(),
                    new Rectangle(),
                    new Rectangle());
            }

            int rowBlockH = cardH + outcomeGap + outcomeH;
            int sceneH = rowBlockH * 2 + rowGap;
            int topY = centerY - sceneH / 2;
            int topRowWidth = cardW * 2 + horizontalGap;
            int bottomRowWidth = cardW * 3 + horizontalGap * 2;
            int topX = centerX - topRowWidth / 2;
            int bottomX = centerX - bottomRowWidth / 2;
            int bottomY = topY + rowBlockH + rowGap;

            List<Rectangle> baseCards = new ArrayList<>(5);
            List<Rectangle> baseOutcomes = new ArrayList<>(5);
            for (int position = 0; position < 5; position++)
            {
                boolean topRow = position < 2;
                int rowIndex = topRow ? position : position - 2;
                int x = (topRow ? topX : bottomX)
                    + rowIndex * (cardW + horizontalGap);
                int y = topRow ? topY : bottomY;
                baseCards.add(new Rectangle(x, y, cardW, cardH));
                baseOutcomes.add(new Rectangle(
                    x - Math.max(2, horizontalGap / 10),
                    y + cardH + outcomeGap,
                    cardW + Math.max(4, horizontalGap / 5),
                    outcomeH));
            }

            List<Rectangle> cards = new ArrayList<>(5);
            List<Rectangle> outcomes = new ArrayList<>(5);
            for (int position = 0; position < 5; position++)
            {
                cards.add(scaleAround(
                    baseCards.get(position),
                    centerX,
                    centerY,
                    zoom));
                outcomes.add(scaleAround(
                    baseOutcomes.get(position),
                    centerX,
                    centerY,
                    zoom));
            }

            int wrapperW = Math.max(130, (int) Math.round(260 * viewportScale));
            int wrapperH = Math.max(195, (int) Math.round(390 * viewportScale));
            Rectangle wrapper = scaleAround(
                new Rectangle(
                    centerX - wrapperW / 2,
                    centerY - wrapperH / 2,
                    wrapperW,
                    wrapperH),
                centerX,
                centerY,
                zoom);

            int skipW = Math.max(64, (int) Math.round(90 * viewportScale));
            Rectangle skip = new Rectangle(
                viewport.x + viewport.width - skipW - 10,
                viewport.y + 8,
                skipW,
                Math.max(25, (int) Math.round(33 * viewportScale)));
            int closeW = Math.max(86, (int) Math.round(112 * viewportScale));
            Rectangle close = new Rectangle(
                viewport.x + viewport.width - closeW - 10,
                viewport.y + viewport.height
                    - Math.max(28, (int) Math.round(36 * viewportScale)) - 8,
                closeW,
                Math.max(28, (int) Math.round(36 * viewportScale)));
            int summaryW = Math.min(
                viewport.width - closeW - 38,
                Math.max(250, (int) Math.round(520 * viewportScale)));
            int summaryH = Math.max(32, (int) Math.round(42 * viewportScale));
            Rectangle summary = new Rectangle(
                viewport.x + 10,
                viewport.y + viewport.height - summaryH - 8,
                Math.max(180, summaryW),
                summaryH);

            return new Layout(
                viewport,
                centerX,
                titleY,
                subtitleY,
                titleSize,
                subtitleSize,
                headingPanelY,
                headingPanelWidth,
                headingPanelHeight,
                wrapper,
                Collections.unmodifiableList(cards),
                Collections.unmodifiableList(outcomes),
                skip,
                close,
                summary);
        }

        private static Rectangle scaleAround(
            Rectangle source,
            int centerX,
            int centerY,
            double zoom)
        {
            double sourceCenterX = source.getCenterX();
            double sourceCenterY = source.getCenterY();
            int width = Math.max(1, (int) Math.round(source.width * zoom));
            int height = Math.max(1, (int) Math.round(source.height * zoom));
            double scaledCenterX = centerX
                + (sourceCenterX - centerX) * zoom;
            double scaledCenterY = centerY
                + (sourceCenterY - centerY) * zoom;
            return new Rectangle(
                (int) Math.round(scaledCenterX - width / 2.0),
                (int) Math.round(scaledCenterY - height / 2.0),
                width,
                height);
        }

        private Rectangle cardBounds(int position)
        {
            return new Rectangle(cards.get(position));
        }

        private Rectangle outcomeBounds(int position)
        {
            return new Rectangle(outcomes.get(position));
        }
    }
}
