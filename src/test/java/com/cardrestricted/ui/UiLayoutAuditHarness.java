package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.collection.activity.CollectionActivitySnapshot;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.foil.FoilEntitlementResolver;
import com.cardrestricted.foil.FoilRewardRegistry;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.presentation.CardArtworkProvider;
import com.cardrestricted.progression.ProgressionMilestonePolicy;
import com.cardrestricted.session.SessionSnapshot;
import com.cardrestricted.starter.StarterRewardState;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/** Deterministic headless geometry, typography and paint audit for the sidebar. */
final class UiLayoutAuditHarness
{
    private static final Dimension[] VIEWPORTS = {
        new Dimension(220, 300),
        new Dimension(243, 420),
        new Dimension(243, 720),
        new Dimension(320, 900),
        new Dimension(480, 1_200)
    };

    private UiLayoutAuditHarness()
    {
    }

    static Result run() throws Exception
    {
        AtomicReference<Result> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            CardRestrictedAccountPanel panel = null;
            try
            {
                CardCatalogue catalogue = MembersCatalogue.create();
                panel = new CardRestrictedAccountPanel(
                    catalogue,
                    new FoilEntitlementResolver(
                        catalogue,
                        FoilRewardRegistry.load(
                            UiLayoutAuditHarness.class.getClassLoader(),
                            catalogue)),
                    new NoOpSetupHandler(),
                    new NoOpPackHandler(),
                    CardArtworkProvider.none());
                List<Scenario> scenarios = List.of(
                    new Scenario("logged_out", SessionSnapshot.loggedOut()),
                    new Scenario("needs_setup", SessionSnapshot.needsSetup(
                        "A Very Long Character Display Name For Setup Layout")),
                    new Scenario("ready_empty", SessionSnapshot.ready(
                        emptyState(catalogue),
                        CollectionActivitySnapshot.empty(),
                        "Ready with no unlocked cards.")),
                    new Scenario("ready_max", SessionSnapshot.ready(
                        maximumContentState(catalogue),
                        CollectionActivitySnapshot.empty(),
                        "A deliberately long status message used to exercise "
                            + "wrapping at minimum RuneLite sidebar widths.")));
                Audit audit = new Audit();
                for (Scenario scenario : scenarios)
                {
                    // Recreate first-login ordering: data can arrive before a
                    // viewport has assigned any usable width.
                    panel.setSize(new Dimension(0, 0));
                    panel.render(scenario.snapshot);
                    for (Dimension viewport : VIEWPORTS)
                    {
                        panel.setPreferredSize(viewport);
                        panel.setSize(viewport);
                        Dimension panelSize = new Dimension(
                            viewport.width,
                            panel.getScrollableTracksViewportHeight()
                                ? viewport.height
                                : Math.max(
                                    viewport.height,
                                    panel.getPreferredSize().height));
                        panel.setSize(panelSize);
                        layoutRecursively(panel);
                        List<JTabbedPane> tabs = visibleTabbedPanes(panel);
                        if (tabs.isEmpty())
                        {
                            audit.capture(panel, scenario.name, viewport, -1);
                            continue;
                        }
                        JTabbedPane primary = tabs.get(0);
                        for (int index = 0; index < primary.getTabCount(); index++)
                        {
                            primary.setSelectedIndex(index);
                            layoutRecursively(panel);
                            audit.capture(panel, scenario.name, viewport, index);
                        }
                    }
                }
                result.set(audit.finish());
            }
            catch (Throwable throwable)
            {
                failure.set(throwable);
            }
            finally
            {
                if (panel != null)
                {
                    panel.closeAuxiliaryWindows();
                }
            }
        });
        if (failure.get() != null)
        {
            throw new AssertionError("UI layout audit failed to execute.",
                failure.get());
        }
        return result.get();
    }

    private static void layoutRecursively(Container container)
    {
        container.doLayout();
        for (Component component : container.getComponents())
        {
            if (component instanceof Container)
            {
                layoutRecursively((Container) component);
            }
        }
    }

    private static List<JTabbedPane> visibleTabbedPanes(Container root)
    {
        List<JTabbedPane> result = new ArrayList<>();
        for (Component component : root.getComponents())
        {
            if (!component.isVisible())
            {
                continue;
            }
            if (component instanceof JTabbedPane)
            {
                result.add((JTabbedPane) component);
            }
            if (component instanceof Container)
            {
                result.addAll(visibleTabbedPanes((Container) component));
            }
        }
        return result;
    }

    private static CollectionState emptyState(CardCatalogue catalogue)
    {
        return new CollectionState(
            UUID.fromString("00000000-0000-0000-0000-000000000894"),
            "ui-audit-empty",
            "UI Audit Empty",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-05T20:00:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            0L,
            0L,
            Set.of(),
            Set.of(),
            Set.of(StarterRewardState.POINTS_CHOICE_MARKER));
    }

    private static CollectionState maximumContentState(
        CardCatalogue catalogue)
    {
        LinkedHashSet<String> owned = new LinkedHashSet<>();
        LinkedHashSet<String> foils = new LinkedHashSet<>();
        int index = 0;
        for (CardDefinition card : catalogue.getCards())
        {
            owned.add(card.getCardId());
            if (index++ < 500)
            {
                foils.add(card.getCardId());
            }
            if (owned.size() == 4_500)
            {
                break;
            }
        }
        Set<String> markers = Set.of(
            StarterRewardState.POINTS_CHOICE_MARKER,
            ProgressionMilestonePolicy.INITIATE_FOIL_MARKER,
            ProgressionMilestonePolicy.HERO_PACK_MARKER,
            ProgressionMilestonePolicy.NOBLE_PACK_MARKER,
            ProgressionMilestonePolicy.LEGEND_PACK_MARKER,
            ProgressionMilestonePolicy.MYTHICAL_PACK_MARKER,
            ProgressionMilestonePolicy.GODS_PACK_MARKER);
        return new CollectionState(
            UUID.fromString("00000000-0000-0000-0000-000000000895"),
            "ui-audit-maximum",
            "A Very Long Character Display Name For Layout Stress",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-05T20:00:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            123_456L,
            9_876_543_210L,
            8_765_432_100L,
            owned,
            foils,
            markers);
    }

    static final class Result
    {
        final int captures;
        final int componentsInspected;
        final int paintCycles;
        final int tabStates;
        final List<String> violations;
        final String structuralSha256;
        final String pixelSha256;

        Result(
            int captures,
            int componentsInspected,
            int paintCycles,
            int tabStates,
            List<String> violations,
            String structuralSha256,
            String pixelSha256)
        {
            this.captures = captures;
            this.componentsInspected = componentsInspected;
            this.paintCycles = paintCycles;
            this.tabStates = tabStates;
            this.violations = List.copyOf(violations);
            this.structuralSha256 = structuralSha256;
            this.pixelSha256 = pixelSha256;
        }
    }

    private static final class Audit
    {
        private final MessageDigest structural = digest();
        private final MessageDigest pixels = digest();
        private final List<String> violations = new ArrayList<>();
        private int captures;
        private int components;
        private int paints;
        private int tabStates;

        void capture(
            CardRestrictedAccountPanel panel,
            String scenario,
            Dimension viewport,
            int selectedTab)
        {
            captures++;
            if (selectedTab >= 0)
            {
                tabStates++;
            }
            String context = scenario + '@' + viewport.width + 'x'
                + viewport.height + "#tab=" + selectedTab;
            update(structural, context);
            inspect(panel, context, 0);
            BufferedImage image = new BufferedImage(
                Math.max(1, viewport.width),
                Math.max(1, viewport.height),
                BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try
            {
                panel.printAll(graphics);
            }
            finally
            {
                graphics.dispose();
            }
            paints++;
            for (int y = 0; y < image.getHeight(); y++)
            {
                for (int x = 0; x < image.getWidth(); x++)
                {
                    int argb = image.getRGB(x, y);
                    pixels.update((byte) (argb >>> 24));
                    pixels.update((byte) (argb >>> 16));
                    pixels.update((byte) (argb >>> 8));
                    pixels.update((byte) argb);
                }
            }
        }

        private void inspect(Component component, String context, int depth)
        {
            if (!component.isVisible())
            {
                return;
            }
            components++;
            Rectangle bounds = component.getBounds();
            Dimension preferred = component.getPreferredSize();
            Font font = component.getFont();
            StringBuilder fingerprint = new StringBuilder()
                .append(depth).append('|')
                .append(component.getClass().getName()).append('|')
                .append(bounds.x).append(',').append(bounds.y).append(',')
                .append(bounds.width).append(',').append(bounds.height)
                .append('|');
            if (preferred != null)
            {
                fingerprint.append(preferred.width).append(',')
                    .append(preferred.height);
            }
            fingerprint.append('|');
            if (font != null)
            {
                fingerprint.append(font.getFamily()).append(',')
                    .append(font.getStyle()).append(',')
                    .append(font.getSize());
            }
            fingerprint.append('|').append(componentText(component));
            update(structural, fingerprint.toString());

            if (bounds.width < 0 || bounds.height < 0)
            {
                violations.add(context + ": negative bounds " + fingerprint);
            }
            if (preferred != null
                && (preferred.width < 0 || preferred.height < 0))
            {
                violations.add(context + ": negative preferred size "
                    + fingerprint);
            }
            assertInsideOrdinaryParent(component, context);
            if (component instanceof JTabbedPane)
            {
                inspectTabs((JTabbedPane) component, context);
            }
            if (component instanceof AbstractButton)
            {
                inspectButton((AbstractButton) component, context);
            }
            if (component instanceof JLabel)
            {
                inspectLabel((JLabel) component, context);
            }
            if (component instanceof JTextArea)
            {
                inspectTextArea((JTextArea) component, context);
            }
            if (component instanceof JScrollPane)
            {
                inspectScrollPane((JScrollPane) component, context);
            }
            if (component instanceof Container)
            {
                for (Component child : ((Container) component).getComponents())
                {
                    inspect(child, context, depth + 1);
                }
            }
        }

        private void inspectTabs(JTabbedPane tabs, String context)
        {
            for (int index = 0; index < tabs.getTabCount(); index++)
            {
                String title = tabs.getTitleAt(index);
                if (title == null || title.trim().isEmpty())
                {
                    violations.add(context + ": blank tab title at " + index);
                }
                else if (title.contains("...") || title.contains("…"))
                {
                    violations.add(context + ": truncated tab title " + title);
                }
                Rectangle bounds = tabs.getBoundsAt(index);
                if (bounds != null && (bounds.x < 0 || bounds.y < 0
                    || bounds.x + bounds.width > tabs.getWidth() + 2
                    || bounds.y + bounds.height > tabs.getHeight() + 2))
                {
                    violations.add(context + ": tab bounds outside pane: "
                        + title + ' ' + bounds + " pane=" + tabs.getBounds());
                }
            }
        }

        private void inspectButton(AbstractButton button, String context)
        {
            String text = button.getText();
            if (text == null || text.isEmpty() || button.getWidth() <= 0
                || text.trim().toLowerCase(java.util.Locale.ROOT)
                    .startsWith("<html"))
            {
                return;
            }
            Insets insets = button.getInsets();
            int available = button.getWidth() - insets.left - insets.right;
            int required = button.getFontMetrics(button.getFont())
                .stringWidth(text);
            if (required > available + 3)
            {
                violations.add(context + ": button text clips: " + text
                    + " required=" + required + " available=" + available);
            }
        }

        private void inspectLabel(JLabel label, String context)
        {
            String text = label.getText();
            if (text == null || text.isEmpty() || label.getWidth() <= 0
                || text.trim().toLowerCase(java.util.Locale.ROOT)
                    .startsWith("<html"))
            {
                return;
            }
            Insets insets = label.getInsets();
            int available = label.getWidth() - insets.left - insets.right;
            int required = label.getFontMetrics(label.getFont())
                .stringWidth(text);
            if (label.getIcon() != null)
            {
                required += label.getIcon().getIconWidth();
                if (!text.isEmpty())
                {
                    required += label.getIconTextGap();
                }
            }
            if (required > available + 3)
            {
                violations.add(context + ": label text clips: " + text
                    + " required=" + required + " available=" + available);
            }
        }

        private void inspectTextArea(JTextArea text, String context)
        {
            if (!text.getLineWrap() || text.getWidth() <= 0)
            {
                return;
            }
            Dimension preferred = text.getPreferredSize();
            if (text.getHeight() + 2 < preferred.height)
            {
                violations.add(context + ": wrapped text clips vertically: "
                    + safe(text.getText()) + " preferred=" + preferred
                    + " actual=" + text.getSize());
            }
        }

        private void inspectScrollPane(JScrollPane scroll, String context)
        {
            if (scroll.getHorizontalScrollBarPolicy()
                != ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
            {
                return;
            }
            JViewport viewport = scroll.getViewport();
            Component view = viewport.getView();
            if (view == null || viewport.getExtentSize().width <= 0)
            {
                return;
            }
            if (!(view instanceof Scrollable)
                || !((Scrollable) view).getScrollableTracksViewportWidth())
            {
                violations.add(context
                    + ": horizontal-scroll-disabled view does not track width: "
                    + view.getClass().getName());
            }
            if (view.getWidth() > viewport.getExtentSize().width + 1)
            {
                violations.add(context + ": scroll view exceeds available width: "
                    + view.getClass().getSimpleName() + " view="
                    + view.getSize() + " extent=" + viewport.getExtentSize());
            }
        }

        private void assertInsideOrdinaryParent(
            Component component,
            String context)
        {
            Container parent = component.getParent();
            if (parent == null
                || parent instanceof JLayeredPane
                || parent instanceof CellRendererPane
                || parent.getWidth() <= 0 || parent.getHeight() <= 0)
            {
                return;
            }
            Rectangle bounds = component.getBounds();
            int tolerance = 2;
            boolean horizontalOutside = bounds.x < -tolerance
                || bounds.x + bounds.width > parent.getWidth() + tolerance;
            boolean verticalOutside = !hasScrollableAncestor(component)
                && (bounds.y < -tolerance
                    || bounds.y + bounds.height
                        > parent.getHeight() + tolerance);
            if (horizontalOutside || verticalOutside)
            {
                violations.add(context + ": component outside parent: "
                    + component.getClass().getSimpleName() + ' ' + bounds
                    + " parent=" + parent.getClass().getSimpleName() + ' '
                    + parent.getBounds());
            }
        }

        Result finish()
        {
            return new Result(captures, components, paints, tabStates,
                violations, hex(structural.digest()), hex(pixels.digest()));
        }
    }


    private static boolean hasScrollableAncestor(Component component)
    {
        Container parent = component.getParent();
        int depth = 0;
        while (parent != null && depth++ < 32)
        {
            if (parent instanceof JViewport || parent instanceof JScrollPane)
            {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static String componentText(Component component)
    {
        if (component instanceof JLabel)
        {
            return safe(((JLabel) component).getText());
        }
        if (component instanceof AbstractButton)
        {
            return safe(((AbstractButton) component).getText());
        }
        if (component instanceof JTabbedPane)
        {
            JTabbedPane tabs = (JTabbedPane) component;
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < tabs.getTabCount(); index++)
            {
                if (index > 0)
                {
                    text.append('/');
                }
                text.append(safe(tabs.getTitleAt(index)));
            }
            return text.toString();
        }
        return "";
    }

    private static String safe(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static MessageDigest digest()
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(exception);
        }
    }

    private static void update(MessageDigest digest, String value)
    {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
        {
            output.append(String.format("%02x", value & 0xff));
        }
        return output.toString();
    }

    private static final class Scenario
    {
        private final String name;
        private final SessionSnapshot snapshot;

        private Scenario(String name, SessionSnapshot snapshot)
        {
            this.name = name;
            this.snapshot = snapshot;
        }
    }

    private static final class NoOpSetupHandler
        implements CollectionSetupHandler
    {
        @Override
        public void createCollection(
            com.cardrestricted.collection.ProfileSetupOptions options)
        {
        }

        @Override
        public void disableIntegrity()
        {
        }

        @Override
        public void resetProfile()
        {
        }

        @Override
        public void exportDiagnostics()
        {
        }
    }

    private static final class NoOpPackHandler implements PackActionHandler
    {
        @Override
        public void redeemStarterPack()
        {
        }

        @Override
        public void purchaseStandardPack()
        {
        }

        @Override
        public void purchaseRareHunterPack()
        {
        }

        @Override
        public void purchaseNoncombatNpcPack()
        {
        }

        @Override
        public void purchaseAttackableNpcPack()
        {
        }

        @Override
        public void exchangeNexusCard(Rarity rarity)
        {
        }
    }
}
