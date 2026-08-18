package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.presentation.CardArtworkProvider;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.LinearGradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Modal album card preview rendered as a perspective-projected 3D card. */
final class CardPreviewDialog extends JDialog
{
    private static final long serialVersionUID = 1L;
    private static final int CARD_WIDTH = 320;
    private static final int CARD_HEIGHT = 480;

    private final JFrame ownerFrame;
    private final PreviewPanel previewPanel;

    CardPreviewDialog(
        JFrame owner,
        CardDefinition card,
        CardArtworkProvider artworkProvider,
        boolean owned,
        Runnable detailsAction)
    {
        this(owner, card, artworkProvider, owned, false, false, detailsAction);
    }

    CardPreviewDialog(
        JFrame owner,
        CardDefinition card,
        CardArtworkProvider artworkProvider,
        boolean owned,
        boolean foil,
        Runnable detailsAction)
    {
        this(owner, card, artworkProvider, owned, foil, false, detailsAction);
    }

    CardPreviewDialog(
        JFrame owner,
        CardDefinition card,
        CardArtworkProvider artworkProvider,
        boolean owned,
        boolean foil,
        boolean foilAccess,
        Runnable detailsAction)
    {
        super(owner, "Card preview", ModalityType.APPLICATION_MODAL);
        ownerFrame = owner;
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(new Color(0, 0, 0, 0));
        previewPanel = new PreviewPanel(
            card,
            artworkProvider,
            owned,
            foil,
            foilAccess,
            this::closePreview,
            () -> {
                closePreview();
                SwingUtilities.invokeLater(detailsAction);
            });
        setContentPane(previewPanel);
        setMinimumSize(new Dimension(640, 560));
        installKeyBindings();
    }

    void open()
    {
        setBounds(ownerFrame.getBounds());
        SwingUtilities.invokeLater(() -> {
            previewPanel.startAnimation();
            previewPanel.revealFront();
        });
        setVisible(true);
    }

    @Override
    public void dispose()
    {
        previewPanel.stopAnimation();
        super.dispose();
    }

    private void closePreview()
    {
        previewPanel.stopAnimation();
        dispose();
    }

    private void installKeyBindings()
    {
        bind(KeyEvent.VK_ESCAPE, "close", this::closePreview);
        bind(KeyEvent.VK_SPACE, "flip", previewPanel::toggleSide);
        bind(KeyEvent.VK_D, "details", previewPanel::openDetails);
    }

    private void bind(int keyCode, String actionName, Runnable action)
    {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(keyCode, 0),
            actionName);
        getRootPane().getActionMap().put(actionName, new AbstractAction()
        {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event)
            {
                action.run();
            }
        });
    }

    private static final class PreviewPanel extends JComponent
    {
        private static final long serialVersionUID = 1L;
        private static final int FLIP_MILLIS = 560;
        private static final double MAX_YAW = Math.toRadians(15.0);
        private static final double MAX_PITCH = Math.toRadians(11.0);
        private static final int MOVING_MESH_COLUMNS = 2;
        private static final int MOVING_MESH_ROWS = 3;
        private static final int STATIC_MESH_COLUMNS = 6;
        private static final int STATIC_MESH_ROWS = 9;
        private static final Color OVERLAY = new Color(0, 0, 0, 224);

        private final transient BufferedImage front;
        private final transient BufferedImage back;
        private final transient BufferedImage mirroredBack;
        private final transient BufferedImage shadedFace = new BufferedImage(
            CARD_WIDTH,
            CARD_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
        private final Rarity rarity;
        private final boolean foil;
        private final transient Runnable closeAction;
        private final transient Runnable detailsAction;
        private final Timer timer;
        private boolean currentFront;
        private boolean fromFront;
        private boolean targetFront;
        private boolean flipping;
        private long flipStartedNanos;
        private double flipProgress = 1.0;
        private double yaw;
        private double pitch;
        private double yawVelocity;
        private double pitchVelocity;
        private double targetYaw;
        private double targetPitch;
        private double pointerX;
        private double pointerY;
        private int lastPointerPixelX = Integer.MIN_VALUE;
        private int lastPointerPixelY = Integer.MIN_VALUE;
        private double centreOffsetX;
        private double centreOffsetY;
        private double targetCentreOffsetX;
        private double targetCentreOffsetY;
        private int settledFrames;
        private int lastShadeKey = Integer.MIN_VALUE;
        private boolean lastShadeFront;

        PreviewPanel(
            CardDefinition card,
            CardArtworkProvider artworkProvider,
            boolean owned,
            Runnable closeAction,
            Runnable detailsAction)
        {
            this(
                card,
                artworkProvider,
                owned,
                false,
                false,
                closeAction,
                detailsAction);
        }

        PreviewPanel(
            CardDefinition card,
            CardArtworkProvider artworkProvider,
            boolean owned,
            boolean foil,
            Runnable closeAction,
            Runnable detailsAction)
        {
            this(card, artworkProvider, owned, foil, false, closeAction, detailsAction);
        }

        PreviewPanel(
            CardDefinition card,
            CardArtworkProvider artworkProvider,
            boolean owned,
            boolean foil,
            boolean foilAccess,
            Runnable closeAction,
            Runnable detailsAction)
        {
            front = roundedTexture(
                CardUiAssets.cardThumbnail(
                    card,
                    artworkProvider,
                    owned,
                    false,
                    foilAccess,
                    CARD_WIDTH,
                    CARD_HEIGHT),
                0);
            back = roundedTexture(
                CardUiAssets.cardBack(CARD_WIDTH, CARD_HEIGHT),
                9);
            mirroredBack = mirrorHorizontally(back);
            rarity = card.getRarity();
            this.foil = foil;
            this.closeAction = closeAction;
            this.detailsAction = detailsAction;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            timer = new Timer(33, event -> advanceAnimation());
            timer.setCoalesce(true);
            installMouseHandling();
        }

        void startAnimation()
        {
            settledFrames = 0;
            if (!timer.isRunning())
            {
                timer.start();
            }
        }

        void stopAnimation()
        {
            timer.stop();
        }

        void revealFront()
        {
            currentFront = false;
            startFlip(true);
            requestFocusInWindow();
        }

        void toggleSide()
        {
            if (!flipping)
            {
                startFlip(!currentFront);
            }
        }

        void openDetails()
        {
            detailsAction.run();
        }

        private void installMouseHandling()
        {
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent event)
                {
                    if (!cardPolygon().contains(event.getPoint()))
                    {
                        closeAction.run();
                        return;
                    }
                    if (event.getClickCount() >= 2)
                    {
                        openDetails();
                    }
                    else
                    {
                        toggleSide();
                    }
                }

                @Override
                public void mouseExited(MouseEvent event)
                {
                    setPointerTarget(0.0, 0.0);
                }
            });
            addMouseMotionListener(new MouseMotionAdapter()
            {
                @Override
                public void mouseMoved(MouseEvent event)
                {
                    if (lastPointerPixelX != Integer.MIN_VALUE
                        && Math.abs(event.getX() - lastPointerPixelX) < 2
                        && Math.abs(event.getY() - lastPointerPixelY) < 2)
                    {
                        return;
                    }
                    lastPointerPixelX = event.getX();
                    lastPointerPixelY = event.getY();
                    double normalizedX = clamp(
                        (event.getX() - getWidth() / 2.0)
                            / Math.max(1.0, getWidth() / 2.0));
                    double normalizedY = clamp(
                        (event.getY() - getHeight() / 2.0)
                            / Math.max(1.0, getHeight() / 2.0));
                    setPointerTarget(normalizedX, normalizedY);
                }
            });
        }

        private void setPointerTarget(double normalizedX, double normalizedY)
        {
            pointerX = normalizedX;
            pointerY = normalizedY;
            settledFrames = 0;
            if (!timer.isRunning())
            {
                    timer.start();
            }
            if (flipping)
            {
                targetYaw = 0.0;
                targetPitch = 0.0;
                targetCentreOffsetX = 0.0;
                targetCentreOffsetY = 0.0;
                return;
            }
            targetYaw = normalizedX * MAX_YAW;
            targetPitch = -normalizedY * MAX_PITCH;
            targetCentreOffsetX = normalizedX * 12.0;
            targetCentreOffsetY = normalizedY * 8.0;
        }

        private static double clamp(double value)
        {
            return Math.max(-1.0, Math.min(1.0, value));
        }

        private void startFlip(boolean showFront)
        {
            fromFront = currentFront;
            targetFront = showFront;
            flipProgress = 0.0;
            flipping = true;
            settledFrames = 0;
            targetYaw = 0.0;
            targetPitch = 0.0;
            flipStartedNanos = System.nanoTime();
            if (!timer.isRunning())
            {
                timer.start();
            }
        }

        private void advanceAnimation()
        {
            Rectangle before = previewDirtyBounds();
            yawVelocity += (targetYaw - yaw) * 0.115;
            yawVelocity *= 0.72;
            yaw += yawVelocity;
            pitchVelocity += (targetPitch - pitch) * 0.115;
            pitchVelocity *= 0.72;
            pitch += pitchVelocity;
            centreOffsetX += (targetCentreOffsetX - centreOffsetX) * 0.16;
            centreOffsetY += (targetCentreOffsetY - centreOffsetY) * 0.16;

            if (flipping)
            {
                long elapsed = System.nanoTime() - flipStartedNanos;
                flipProgress = Math.min(
                    1.0,
                    elapsed / (FLIP_MILLIS * 1_000_000.0));
                if (flipProgress >= 1.0)
                {
                    currentFront = targetFront;
                    flipping = false;
                    flipProgress = 1.0;
                    setPointerTarget(pointerX, pointerY);
                }
            }

            boolean moving = flipping
                || Math.abs(yawVelocity) > 0.00035
                || Math.abs(pitchVelocity) > 0.00035
                || Math.abs(targetYaw - yaw) > 0.0007
                || Math.abs(targetPitch - pitch) > 0.0007
                || Math.abs(targetCentreOffsetX - centreOffsetX) > 0.08
                || Math.abs(targetCentreOffsetY - centreOffsetY) > 0.08;
            if (moving)
            {
                settledFrames = 0;
            }
            else if (++settledFrames >= 10)
            {
                timer.stop();
            }
            Rectangle dirty = before.union(previewDirtyBounds());
            dirty.grow(8, 8);
            repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }

        private Rectangle previewDirtyBounds()
        {
            Rectangle card = polygon(renderState().cardCorners, 0.0, 0.0)
                .getBounds();
            card.grow(58, 52);
            Rectangle base = baseCardBounds();
            Rectangle instructions = new Rectangle(
                0,
                Math.max(0, base.y + base.height + 8),
                getWidth(),
                52);
            return card.union(instructions);
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
                g.setColor(OVERLAY);
                g.fillRect(0, 0, getWidth(), getHeight());

                RenderState state = renderState();
                paintShadow(g, state.cardCorners);
                paintPerspectiveCard(g, state);
                paintInstructions(g);
            }
            finally
            {
                g.dispose();
            }
        }

        private RenderState renderState()
        {
            Rectangle bounds = baseCardBounds();
            double easedFlip = easeInOut(flipProgress);
            double startSide = fromFront ? 0.0 : Math.PI;
            double endSide = targetFront ? 0.0 : Math.PI;
            double sideAngle = flipping
                ? startSide + (endSide - startSide) * easedFlip
                : (currentFront ? 0.0 : Math.PI);
            double totalYaw = yaw + sideAngle;
            boolean frontVisible = Math.cos(sideAngle) >= 0.0;
            BufferedImage source = frontVisible ? front : mirroredBack;
            Projection projection = new Projection(
                bounds.getCenterX() + centreOffsetX,
                bounds.getCenterY() + centreOffsetY,
                bounds.width,
                bounds.height,
                totalYaw,
                pitch,
                Math.max(850.0, bounds.height * 2.25));
            ProjectedPoint[] corners = projection.cardCorners();
            return new RenderState(
                projection,
                corners,
                source,
                frontVisible,
                sideAngle);
        }

        private void paintPerspectiveCard(Graphics2D g, RenderState state)
        {
            double signedFacing = Math.cos(state.sideAngle);
            if (Math.abs(signedFacing) < 0.018)
            {
                drawCardEdge(g, state.cardCorners);
                return;
            }
            boolean moving = flipping
                || Math.abs(yawVelocity) > 0.0008
                || Math.abs(pitchVelocity) > 0.0008
                || Math.abs(targetYaw - yaw) > 0.0015
                || Math.abs(targetPitch - pitch) > 0.0015;
            BufferedImage lit = shadeFace(
                state.source,
                state.frontVisible,
                state.projection);
            PerspectiveMeshRenderer.draw(
                g,
                lit,
                state.projection,
                moving ? MOVING_MESH_COLUMNS : STATIC_MESH_COLUMNS,
                moving ? MOVING_MESH_ROWS : STATIC_MESH_ROWS,
                moving);
            Path2D outline = polygon(state.cardCorners, 0.0, 0.0);
            g.setColor(new Color(231, 196, 125, 115));
            g.draw(outline);
        }

        private int shadeKey(Projection projection)
        {
            int qYaw = (int) Math.round(projection.yaw * 20.0);
            int qPitch = (int) Math.round(projection.pitch * 20.0);
            int qPointerX = (int) Math.round(pointerX * 12.0);
            int qPointerY = (int) Math.round(pointerY * 12.0);
            int key = qYaw;
            key = 31 * key + qPitch;
            key = 31 * key + qPointerX;
            key = 31 * key + qPointerY;
            return key;
        }

        private BufferedImage shadeFace(
            BufferedImage source,
            boolean frontVisible,
            Projection projection)
        {
            int shadeKey = shadeKey(projection);
            if (shadeKey == lastShadeKey && frontVisible == lastShadeFront)
            {
                return shadedFace;
            }
            lastShadeKey = shadeKey;
            lastShadeFront = frontVisible;
            BufferedImage target = shadedFace;
            Graphics2D g = target.createGraphics();
            try
            {
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, target.getWidth(), target.getHeight());
                g.setComposite(AlphaComposite.SrcOver);
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.scale(
                    target.getWidth() / (double) CARD_WIDTH,
                    target.getHeight() / (double) CARD_HEIGHT);
                g.drawImage(source, 0, 0, CARD_WIDTH, CARD_HEIGHT, null);
                Shape clip = new RoundRectangle2D.Double(
                    0,
                    0,
                    CARD_WIDTH,
                    CARD_HEIGHT,
                    24,
                    24);
                g.clip(clip);

                double visibleYaw = Math.asin(clamp(Math.sin(projection.yaw)));
                int farAlpha = (int) Math.round(
                    34 + 86 * Math.min(1.0, Math.abs(visibleYaw) / MAX_YAW));
                boolean lightFromLeft = pointerX <= 0.0;
                int startX = lightFromLeft ? 0 : CARD_WIDTH;
                int endX = lightFromLeft ? CARD_WIDTH : 0;
                g.setPaint(new GradientPaint(
                    startX,
                    0,
                    new Color(0, 0, 0, 0),
                    endX,
                    0,
                    new Color(0, 0, 0, farAlpha)));
                g.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);

                float lightX = (float) ((pointerX * 0.42 + 0.5) * CARD_WIDTH);
                float lightY = (float) ((pointerY * 0.34 + 0.46) * CARD_HEIGHT);
                int strength = rarity.ordinal() >= Rarity.RARE.ordinal()
                    ? 82
                    : 58;
                if (!frontVisible)
                {
                    strength = Math.max(42, strength - 20);
                }
                RadialGradientPaint highlight = new RadialGradientPaint(
                    new Point2D.Float(lightX, lightY),
                    190f,
                    new float[]{0f, 0.28f, 0.68f, 1f},
                    new Color[]{
                        new Color(255, 248, 220, strength),
                        new Color(255, 235, 185, strength / 2),
                        new Color(255, 255, 255, 14),
                        new Color(255, 255, 255, 0)
                    });
                g.setPaint(highlight);
                g.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);

                int sheenX = (int) Math.round(
                    (pointerX * 0.5 + 0.5) * CARD_WIDTH);
                g.setPaint(new GradientPaint(
                    sheenX - 95,
                    0,
                    new Color(255, 255, 255, 0),
                    sheenX + 65,
                    CARD_HEIGHT,
                    new Color(255, 255, 255,
                        rarity.ordinal() >= Rarity.EPIC.ordinal() ? 42 : 24),
                    true));
                g.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);
                if (foil && frontVisible)
                {
                    paintFoilSurface(g, sheenX);
                }
            }
            finally
            {
                g.dispose();
            }
            return target;
        }

        private void paintFoilSurface(Graphics2D g, int sheenX)
        {
            Composite previous = g.getComposite();
            double movement = Math.min(
                1.0,
                Math.abs(yaw) / MAX_YAW * 0.62
                    + Math.abs(pitch) / MAX_PITCH * 0.38);
            float intensity = (float) (0.22 + movement * 0.18);
            float shift = (float) ((pointerX * 0.5 + 0.5) * CARD_WIDTH);
            float vertical = (float) ((pointerY * 0.5 + 0.5) * CARD_HEIGHT);

            LinearGradientPaint iridescence = new LinearGradientPaint(
                shift - CARD_WIDTH * 0.82f,
                CARD_HEIGHT - vertical * 0.32f,
                shift + CARD_WIDTH * 0.82f,
                vertical * 0.42f,
                new float[]{0f, 0.18f, 0.38f, 0.58f, 0.78f, 1f},
                new Color[]{
                    new Color(255, 76, 171, 118),
                    new Color(255, 179, 78, 106),
                    new Color(246, 244, 112, 96),
                    new Color(74, 238, 196, 110),
                    new Color(90, 161, 255, 116),
                    new Color(191, 100, 255, 118)
                });
            g.setComposite(AlphaComposite.SrcOver.derive(intensity));
            g.setPaint(iridescence);
            g.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);

            int sweepWidth = 84 + (int) Math.round(movement * 36.0);
            g.setComposite(AlphaComposite.SrcOver.derive(
                (float) (0.18 + movement * 0.24)));
            g.setPaint(new GradientPaint(
                sheenX - sweepWidth,
                CARD_HEIGHT,
                new Color(255, 255, 255, 0),
                sheenX + sweepWidth,
                0,
                new Color(255, 255, 246, 185),
                true));
            g.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);

            int twinkles = 5 + rarity.ordinal();
            for (int index = 0; index < twinkles; index++)
            {
                double phase = index * 2.399963229728653
                    + pointerX * 1.8
                    - pointerY * 1.2;
                int x = (int) Math.round(
                    (0.5 + 0.43 * Math.sin(phase * 1.31)) * CARD_WIDTH);
                int y = (int) Math.round(
                    (0.5 + 0.42 * Math.cos(phase * 0.87)) * CARD_HEIGHT);
                double facing = 0.5 + 0.5 * Math.sin(
                    phase * 2.1 + pointerX * 3.2 - pointerY * 2.4);
                int arm = 2 + index % 3;
                float alpha = (float) Math.min(
                    0.82,
                    0.16 + movement * 0.34 + facing * 0.22);
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g.setColor(new Color(255, 255, 246));
                g.drawLine(x - arm, y, x + arm, y);
                g.drawLine(x, y - arm, x, y + arm);
            }
            g.setComposite(previous);
        }

        private void paintShadow(Graphics2D g, ProjectedPoint[] corners)
        {
            Composite previous = g.getComposite();
            for (int layer = 5; layer >= 1; layer--)
            {
                float alpha = 0.018f * layer;
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g.setColor(Color.BLACK);
                g.fill(polygon(
                    corners,
                    12.0 + layer * 2.0,
                    16.0 + layer * 2.0));
            }
            g.setComposite(previous);
        }

        private void drawCardEdge(Graphics2D g, ProjectedPoint[] corners)
        {
            g.setColor(new Color(214, 174, 101));
            g.drawLine(
                (int) Math.round(corners[0].x),
                (int) Math.round(corners[0].y),
                (int) Math.round(corners[3].x),
                (int) Math.round(corners[3].y));
        }

        private void paintInstructions(Graphics2D g)
        {
            String line = "Click card to flip  |  Double-click or D for details  |  Esc or click outside to close";
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            FontMetrics metrics = g.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(line)) / 2;
            Rectangle bounds = baseCardBounds();
            int y = Math.min(getHeight() - 18, bounds.y + bounds.height + 34);
            g.setColor(new Color(225, 225, 225, 210));
            g.drawString(line, Math.max(12, x), y);
        }

        private Shape cardPolygon()
        {
            return polygon(renderState().cardCorners, 0.0, 0.0);
        }

        private Rectangle baseCardBounds()
        {
            int width = Math.min(CARD_WIDTH + 18, Math.max(236, getWidth() - 96));
            int height = Math.round(width * CARD_HEIGHT / (float) CARD_WIDTH);
            if (height > getHeight() - 112)
            {
                height = Math.max(344, getHeight() - 112);
                width = Math.round(height * CARD_WIDTH / (float) CARD_HEIGHT);
            }
            return new Rectangle(
                (getWidth() - width) / 2,
                Math.max(24, (getHeight() - height) / 2 - 8),
                width,
                height);
        }

        private static double easeInOut(double value)
        {
            double clamped = Math.max(0.0, Math.min(1.0, value));
            return clamped < 0.5
                ? 4.0 * clamped * clamped * clamped
                : 1.0 - Math.pow(-2.0 * clamped + 2.0, 3.0) / 2.0;
        }

        private static Path2D polygon(
            ProjectedPoint[] points,
            double offsetX,
            double offsetY)
        {
            Path2D path = new Path2D.Double();
            path.moveTo(points[0].x + offsetX, points[0].y + offsetY);
            for (int index = 1; index < points.length; index++)
            {
                path.lineTo(points[index].x + offsetX, points[index].y + offsetY);
            }
            path.closePath();
            return path;
        }

        private static BufferedImage roundedTexture(
            BufferedImage source,
            int crop)
        {
            BufferedImage result = new BufferedImage(
                CARD_WIDTH,
                CARD_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();
            try
            {
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setClip(new RoundRectangle2D.Double(
                    1,
                    1,
                    CARD_WIDTH - 2.0,
                    CARD_HEIGHT - 2.0,
                    25,
                    25));
                g.drawImage(
                    source,
                    0,
                    0,
                    CARD_WIDTH,
                    CARD_HEIGHT,
                    crop,
                    crop,
                    Math.max(crop + 1, source.getWidth() - crop),
                    Math.max(crop + 1, source.getHeight() - crop),
                    null);
            }
            finally
            {
                g.dispose();
            }
            return result;
        }

        private static BufferedImage mirrorHorizontally(BufferedImage source)
        {
            BufferedImage result = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();
            try
            {
                g.drawImage(
                    source,
                    source.getWidth(),
                    0,
                    -source.getWidth(),
                    source.getHeight(),
                    null);
            }
            finally
            {
                g.dispose();
            }
            return result;
        }
    }

    private static final class RenderState
    {
        private final Projection projection;
        private final ProjectedPoint[] cardCorners;
        private final BufferedImage source;
        private final boolean frontVisible;
        private final double sideAngle;

        private RenderState(
            Projection projection,
            ProjectedPoint[] cardCorners,
            BufferedImage source,
            boolean frontVisible,
            double sideAngle)
        {
            this.projection = projection;
            this.cardCorners = cardCorners;
            this.source = source;
            this.frontVisible = frontVisible;
            this.sideAngle = sideAngle;
        }
    }

    private static final class Projection
    {
        private final double centerX;
        private final double centerY;
        private final double width;
        private final double height;
        private final double yaw;
        private final double pitch;
        private final double cameraDistance;
        private final double cosYaw;
        private final double sinYaw;
        private final double cosPitch;
        private final double sinPitch;

        private Projection(
            double centerX,
            double centerY,
            double width,
            double height,
            double yaw,
            double pitch,
            double cameraDistance)
        {
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = width;
            this.height = height;
            this.yaw = yaw;
            this.pitch = pitch;
            this.cameraDistance = cameraDistance;
            cosYaw = Math.cos(yaw);
            sinYaw = Math.sin(yaw);
            cosPitch = Math.cos(pitch);
            sinPitch = Math.sin(pitch);
        }

        private ProjectedPoint[] cardCorners()
        {
            return new ProjectedPoint[]{
                projectWorld(-width / 2.0, -height / 2.0, 0.0),
                projectWorld(width / 2.0, -height / 2.0, 0.0),
                projectWorld(width / 2.0, height / 2.0, 0.0),
                projectWorld(-width / 2.0, height / 2.0, 0.0)
            };
        }

        private ProjectedPoint projectWorld(double x, double y, double z)
        {
            double rotatedX = x * cosYaw + z * sinYaw;
            double yawDepth = -x * sinYaw + z * cosYaw;
            double rotatedY = y * cosPitch - yawDepth * sinPitch;
            double depth = y * sinPitch + yawDepth * cosPitch;
            double denominator = Math.max(80.0, cameraDistance - depth);
            double factor = cameraDistance / denominator;
            return new ProjectedPoint(
                centerX + rotatedX * factor,
                centerY + rotatedY * factor,
                depth);
        }
    }

    private static final class ProjectedPoint
    {
        private final double x;
        private final double y;
        private final double depth;

        private ProjectedPoint(double x, double y, double depth)
        {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }


    /** Full-resolution mesh projection using Java2D triangle mapping. */
    private static final class PerspectiveMeshRenderer
    {
        private PerspectiveMeshRenderer()
        {
        }

        private static void draw(
            Graphics2D graphics,
            BufferedImage source,
            Projection projection,
            int columns,
            int rows,
            boolean moving)
        {
            Object previousInterpolation = graphics.getRenderingHint(
                RenderingHints.KEY_INTERPOLATION);
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                moving
                    ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                    : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            ProjectedPoint[][] points = new ProjectedPoint[rows + 1][columns + 1];
            for (int row = 0; row <= rows; row++)
            {
                double v = row / (double) rows;
                double worldY = (v - 0.5) * projection.height;
                for (int column = 0; column <= columns; column++)
                {
                    double u = column / (double) columns;
                    double worldX = (u - 0.5) * projection.width;
                    points[row][column] = projection.projectWorld(
                        worldX,
                        worldY,
                        0.0);
                }
            }
            for (int row = 0; row < rows; row++)
            {
                double sourceY0 = row * sourceHeight / (double) rows;
                double sourceY1 = (row + 1) * sourceHeight / (double) rows;
                for (int column = 0; column < columns; column++)
                {
                    double sourceX0 = column * sourceWidth / (double) columns;
                    double sourceX1 = (column + 1) * sourceWidth / (double) columns;
                    ProjectedPoint topLeft = points[row][column];
                    ProjectedPoint topRight = points[row][column + 1];
                    ProjectedPoint bottomRight = points[row + 1][column + 1];
                    ProjectedPoint bottomLeft = points[row + 1][column];
                    drawTriangle(
                        graphics,
                        source,
                        sourceX0, sourceY0,
                        sourceX1, sourceY0,
                        sourceX1, sourceY1,
                        topLeft, topRight, bottomRight);
                    drawTriangle(
                        graphics,
                        source,
                        sourceX0, sourceY0,
                        sourceX1, sourceY1,
                        sourceX0, sourceY1,
                        topLeft, bottomRight, bottomLeft);
                }
            }
            if (previousInterpolation != null)
            {
                graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    previousInterpolation);
            }
        }

        private static void drawTriangle(
            Graphics2D graphics,
            BufferedImage source,
            double sourceX0,
            double sourceY0,
            double sourceX1,
            double sourceY1,
            double sourceX2,
            double sourceY2,
            ProjectedPoint destination0,
            ProjectedPoint destination1,
            ProjectedPoint destination2)
        {
            double determinant = sourceX0 * (sourceY1 - sourceY2)
                + sourceX1 * (sourceY2 - sourceY0)
                + sourceX2 * (sourceY0 - sourceY1);
            if (Math.abs(determinant) < 1.0e-8)
            {
                return;
            }
            double m00 = (destination0.x * (sourceY1 - sourceY2)
                + destination1.x * (sourceY2 - sourceY0)
                + destination2.x * (sourceY0 - sourceY1)) / determinant;
            double m01 = (destination0.x * (sourceX2 - sourceX1)
                + destination1.x * (sourceX0 - sourceX2)
                + destination2.x * (sourceX1 - sourceX0)) / determinant;
            double m02 = (destination0.x * (sourceX1 * sourceY2 - sourceX2 * sourceY1)
                + destination1.x * (sourceX2 * sourceY0 - sourceX0 * sourceY2)
                + destination2.x * (sourceX0 * sourceY1 - sourceX1 * sourceY0))
                / determinant;
            double m10 = (destination0.y * (sourceY1 - sourceY2)
                + destination1.y * (sourceY2 - sourceY0)
                + destination2.y * (sourceY0 - sourceY1)) / determinant;
            double m11 = (destination0.y * (sourceX2 - sourceX1)
                + destination1.y * (sourceX0 - sourceX2)
                + destination2.y * (sourceX1 - sourceX0)) / determinant;
            double m12 = (destination0.y * (sourceX1 * sourceY2 - sourceX2 * sourceY1)
                + destination1.y * (sourceX2 * sourceY0 - sourceX0 * sourceY2)
                + destination2.y * (sourceX0 * sourceY1 - sourceX1 * sourceY0))
                / determinant;

            Path2D clip = new Path2D.Double();
            clip.moveTo(destination0.x, destination0.y);
            clip.lineTo(destination1.x, destination1.y);
            clip.lineTo(destination2.x, destination2.y);
            clip.closePath();
            Graphics2D triangle = (Graphics2D) graphics.create();
            try
            {
                triangle.clip(clip);
                triangle.drawImage(
                    source,
                    new AffineTransform(m00, m10, m01, m11, m02, m12),
                    null);
            }
            finally
            {
                triangle.dispose();
            }
        }
    }
}
