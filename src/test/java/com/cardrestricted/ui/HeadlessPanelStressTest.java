package com.cardrestricted.ui;

import com.cardrestricted.PluginBuildInfo;
import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.foil.FoilEntitlementResolver;
import com.cardrestricted.foil.FoilRewardRegistry;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.presentation.CardArtworkProvider;
import com.cardrestricted.progression.ProgressionMilestonePolicy;
import com.cardrestricted.session.SessionSnapshot;
import com.cardrestricted.session.SessionFailureCode;
import com.cardrestricted.starter.StarterRewardState;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Renders the complete side panel without RuneLite at narrow, short, tall and
 * high-content dimensions. The gate catches layout exceptions, negative sizes,
 * unstable tab switching and paint failures before a user reaches the client.
 */
public final class HeadlessPanelStressTest
{
    private static final Dimension[] VIEWPORTS = {
        new Dimension(220, 320),
        new Dimension(243, 520),
        new Dimension(300, 900),
        new Dimension(480, 1_200)
    };

    @Test
    public void readyPanelSurvivesRepeatedLayoutTabAndPaintCycles()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CollectionState state = maximumContentState(catalogue);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Long> elapsedMillis = new AtomicReference<>(0L);

        SwingUtilities.invokeAndWait(() -> {
            CardRestrictedAccountPanel panel = null;
            try
            {
                panel = new CardRestrictedAccountPanel(
                    catalogue,
                    new FoilEntitlementResolver(
                        catalogue,
                        FoilRewardRegistry.load(
                            getClass().getClassLoader(), catalogue)),
                    new NoOpSetupHandler(),
                    new NoOpPackHandler(),
                    CardArtworkProvider.none());
                SessionSnapshot snapshot = SessionSnapshot.ready(
                    state,
                    com.cardrestricted.collection.activity
                        .CollectionActivitySnapshot.empty(),
                    "A deliberately long status message used to exercise "
                        + "wrapping at minimum RuneLite sidebar widths.");
                long started = System.nanoTime();
                SessionSnapshot[] snapshots = {
                    snapshot,
                    SessionSnapshot.needsSetup(
                        "A Very Long Character Display Name For Setup Stress"),
                    SessionSnapshot.loggedOut()
                };
                for (int cycle = 0; cycle < 12; cycle++)
                {
                    for (SessionSnapshot rendered : snapshots)
                    {
                        panel.render(rendered);
                        for (Dimension viewport : VIEWPORTS)
                        {
                            panel.setSize(viewport);
                            panel.setPreferredSize(viewport);
                            layoutRecursively(panel);
                            switchEveryTab(panel);
                            layoutRecursively(panel);
                            paint(panel, viewport);
                            assertNonNegativeGeometry(panel);
                        }
                    }
                }
                panel.render(snapshot);
                scaleFontsRecursively(panel, 1.5f);
                Dimension highDpiViewport = new Dimension(360, 1_000);
                panel.setSize(highDpiViewport);
                panel.setPreferredSize(highDpiViewport);
                layoutRecursively(panel);
                switchEveryTab(panel);
                layoutRecursively(panel);
                paint(panel, highDpiViewport);
                assertNonNegativeGeometry(panel);
                elapsedMillis.set(
                    (System.nanoTime() - started) / 1_000_000L);
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
            throw new AssertionError(
                "Headless side-panel stress failed.", failure.get());
        }
        assertTrue(
            "Headless panel stress exceeded 30 seconds: "
                + elapsedMillis.get() + " ms",
            elapsedMillis.get() < 30_000L);
    }

    @Test
    public void recoveryActionsRemainReachableAfterLoadFailure()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        AtomicReference<Set<String>> buttons = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CardRestrictedAccountPanel panel = null;
            try
            {
                panel = createPanel(catalogue);
                panel.render(SessionSnapshot.error(
                    "Recovery Character",
                    SessionFailureCode.LOAD_FAILED));
                panel.setSize(new Dimension(243, 520));
                layoutRecursively(panel);
                buttons.set(visibleEnabledButtonTexts(panel));
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
            throw new AssertionError(
                "Load-error recovery controls failed.", failure.get());
        }
        assertTrue(buttons.get().contains("Import save"));
        assertTrue(buttons.get().contains("Export diagnostics"));
        assertTrue(!buttons.get().contains("Export save"));
        assertTrue(!buttons.get().contains("Restore backup"));
    }

    @Test
    public void readyPanelExposesVersionedSaveControls()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        AtomicReference<Set<String>> buttons = new AtomicReference<>();
        AtomicReference<Set<String>> labels = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CardRestrictedAccountPanel panel = null;
            try
            {
                panel = createPanel(catalogue);
                panel.render(SessionSnapshot.ready(
                    maximumContentState(catalogue)));
                panel.setSize(new Dimension(300, 900));
                layoutRecursively(panel);
                buttons.set(visibleEnabledButtonTexts(panel));
                labels.set(visibleLabelTexts(panel));
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
            throw new AssertionError(
                "Ready save controls failed.", failure.get());
        }
        for (String action : Set.of(
            "Export save",
            "Import save",
            "Restore backup",
            "Export diagnostics"))
        {
            assertTrue("Missing enabled ready action: " + action,
                buttons.get().contains(action));
        }
        assertTrue(labels.get().contains(
            PluginBuildInfo.VERSION
                + " / C" + catalogue.getCatalogueVersion()));
    }

    @Test
    public void questRowsAreCompactOnLoginAndStableAfterRepeatedToggles()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        AtomicReference<CardRestrictedAccountPanel> panelReference =
            new AtomicReference<>();
        AtomicReference<Integer> baselineHeight = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CardRestrictedAccountPanel panel = createPanel(catalogue);
            panel.setSize(new Dimension(243, 720));
            panel.render(SessionSnapshot.ready(maximumContentState(catalogue)));
            selectPrimaryTab(panel, "Quest");
            layoutRecursively(panel);
            panelReference.set(panel);
            baselineHeight.set(activeTabViewPreferredHeight(panel));
        });

        CardRestrictedAccountPanel panel = panelReference.get();
        try
        {
            for (int cycle = 0; cycle < 18; cycle++)
            {
                AtomicReference<JButton> toggled = new AtomicReference<>();
                int index = cycle;
                SwingUtilities.invokeAndWait(() -> {
                    java.util.List<JButton> toggles = questToggleButtons(panel);
                    assertTrue("Quest rows did not expose expand controls.",
                        !toggles.isEmpty());
                    JButton button = toggles.get(index % Math.min(8, toggles.size()));
                    toggled.set(button);
                    button.doClick();
                    layoutRecursively(panel);
                });
                // Flush the deferred scroll-anchor correction before the next
                // user action, matching real repeated clicks on the EDT.
                SwingUtilities.invokeAndWait(() -> layoutRecursively(panel));
                SwingUtilities.invokeAndWait(() -> {
                    toggled.get().doClick();
                    layoutRecursively(panel);
                });
                SwingUtilities.invokeAndWait(() -> layoutRecursively(panel));

                AtomicReference<Integer> collapsedHeight =
                    new AtomicReference<>();
                SwingUtilities.invokeAndWait(() -> collapsedHeight.set(
                    activeTabViewPreferredHeight(panel)));
                assertTrue(
                    "Quest list height changed after expand/collapse cycle "
                        + cycle + ": initial=" + baselineHeight.get()
                        + " current=" + collapsedHeight.get(),
                    baselineHeight.get().equals(collapsedHeight.get()));
            }
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::closeAuxiliaryWindows);
        }
    }

    @Test
    public void nexusBalanceAppearsOnlyInPersistentHeader()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        AtomicReference<Integer> count = new AtomicReference<>();
        AtomicReference<CardRestrictedAccountPanel> panelReference =
            new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            CardRestrictedAccountPanel panel = createPanel(catalogue);
            panel.setSize(new Dimension(243, 720));
            panel.render(SessionSnapshot.ready(maximumContentState(catalogue)));
            selectPrimaryTab(panel, "The Nexus");
            layoutRecursively(panel);
            count.set(countVisibleLabelText(panel, "NEXUS SHARDS"));
            panelReference.set(panel);
        });
        try
        {
            assertTrue("Nexus Shards balance is duplicated in the Nexus tab.",
                count.get() == 1);
        }
        finally
        {
            SwingUtilities.invokeAndWait(
                panelReference.get()::closeAuxiliaryWindows);
        }
    }

    private static CardRestrictedAccountPanel createPanel(
        CardCatalogue catalogue)
    {
        return new CardRestrictedAccountPanel(
            catalogue,
            new FoilEntitlementResolver(
                catalogue,
                FoilRewardRegistry.load(
                    HeadlessPanelStressTest.class.getClassLoader(),
                    catalogue)),
            new NoOpSetupHandler(),
            new NoOpPackHandler(),
            CardArtworkProvider.none());
    }

    private static Set<String> visibleEnabledButtonTexts(Container root)
    {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectVisibleText(root, values, true);
        return values;
    }

    private static Set<String> visibleLabelTexts(Container root)
    {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectVisibleText(root, values, false);
        return values;
    }

    private static void collectVisibleText(
        Container root,
        Set<String> values,
        boolean buttons)
    {
        for (Component component : root.getComponents())
        {
            if (!component.isVisible())
            {
                continue;
            }
            if (buttons
                && component instanceof JButton
                && component.isEnabled())
            {
                values.add(((JButton) component).getText());
            }
            else if (!buttons && component instanceof JLabel)
            {
                values.add(((JLabel) component).getText());
            }
            if (component instanceof Container)
            {
                collectVisibleText((Container) component, values, buttons);
            }
        }
    }

    private static void switchEveryTab(Container root)
    {
        for (Component component : root.getComponents())
        {
            if (!component.isVisible())
            {
                continue;
            }
            if (component instanceof JTabbedPane)
            {
                JTabbedPane tabs = (JTabbedPane) component;
                for (int index = 0; index < tabs.getTabCount(); index++)
                {
                    tabs.setSelectedIndex(index);
                    layoutRecursively(tabs);
                }
            }
            if (component instanceof Container)
            {
                switchEveryTab((Container) component);
            }
        }
    }

    private static void selectPrimaryTab(Container root, String title)
    {
        for (Component component : root.getComponents())
        {
            if (component instanceof JTabbedPane)
            {
                JTabbedPane tabs = (JTabbedPane) component;
                for (int index = 0; index < tabs.getTabCount(); index++)
                {
                    if (title.equals(tabs.getTitleAt(index)))
                    {
                        tabs.setSelectedIndex(index);
                        return;
                    }
                }
            }
            if (component instanceof Container)
            {
                selectPrimaryTab((Container) component, title);
            }
        }
    }

    private static int activeTabViewPreferredHeight(Container root)
    {
        for (Component component : root.getComponents())
        {
            if (component instanceof JTabbedPane)
            {
                Component selected = ((JTabbedPane) component)
                    .getSelectedComponent();
                if (selected instanceof JScrollPane)
                {
                    Component view = ((JScrollPane) selected)
                        .getViewport().getView();
                    if (view != null)
                    {
                        return view.getPreferredSize().height;
                    }
                }
            }
            if (component instanceof Container)
            {
                int nested = activeTabViewPreferredHeight(
                    (Container) component);
                if (nested >= 0)
                {
                    return nested;
                }
            }
        }
        return -1;
    }

    private static java.util.List<JButton> questToggleButtons(Container root)
    {
        java.util.List<JButton> buttons = new java.util.ArrayList<>();
        collectQuestToggleButtons(root, buttons);
        return buttons;
    }

    private static void collectQuestToggleButtons(
        Container root,
        java.util.List<JButton> buttons)
    {
        for (Component component : root.getComponents())
        {
            if (!component.isVisible())
            {
                continue;
            }
            if (component instanceof JButton)
            {
                String text = ((JButton) component).getText();
                if ("+".equals(text) || "−".equals(text))
                {
                    buttons.add((JButton) component);
                }
            }
            if (component instanceof Container)
            {
                collectQuestToggleButtons((Container) component, buttons);
            }
        }
    }

    private static int countVisibleLabelText(
        Container root,
        String expected)
    {
        int count = 0;
        for (Component component : root.getComponents())
        {
            if (!component.isVisible())
            {
                continue;
            }
            if (component instanceof JLabel
                && expected.equals(((JLabel) component).getText()))
            {
                count++;
            }
            if (component instanceof Container)
            {
                count += countVisibleLabelText(
                    (Container) component,
                    expected);
            }
        }
        return count;
    }

    private static void layoutRecursively(Container container)
    {
        container.doLayout();
        for (Component child : container.getComponents())
        {
            if (child instanceof Container)
            {
                layoutRecursively((Container) child);
            }
        }
    }

    private static void scaleFontsRecursively(
        Container container,
        float scale)
    {
        for (Component child : container.getComponents())
        {
            if (child.getFont() != null)
            {
                child.setFont(CardUiTheme.scaled(child.getFont(), scale));
            }
            if (child instanceof Container)
            {
                scaleFontsRecursively((Container) child, scale);
            }
        }
    }

    private static void paint(JComponent component, Dimension dimension)
    {
        BufferedImage image = new BufferedImage(
            Math.max(1, dimension.width),
            Math.max(1, dimension.height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            component.printAll(graphics);
        }
        finally
        {
            graphics.dispose();
        }
    }

    private static void assertNonNegativeGeometry(Container container)
    {
        for (Component component : container.getComponents())
        {
            if (!component.isVisible())
            {
                continue;
            }
            String description = componentDescription(component);
            assertTrue("Negative width for " + description,
                component.getWidth() >= 0);
            assertTrue("Negative height for " + description,
                component.getHeight() >= 0);
            Dimension preferred = component.getPreferredSize();
            if (preferred != null)
            {
                assertTrue("Negative preferred width for "
                        + component.getClass(),
                    preferred.width >= 0);
                assertTrue("Negative preferred height for "
                        + component.getClass(),
                    preferred.height >= 0);
            }
            if (component instanceof Container)
            {
                assertNonNegativeGeometry((Container) component);
            }
        }
    }


    private static String componentDescription(Component component)
    {
        StringBuilder value = new StringBuilder(component.getClass().getName())
            .append(" bounds=").append(component.getBounds());
        if (component instanceof javax.swing.JLabel)
        {
            value.append(" text=")
                .append(((javax.swing.JLabel) component).getText());
        }
        Container parent = component.getParent();
        int depth = 0;
        while (parent != null && depth++ < 8)
        {
            value.append(" <- ").append(parent.getClass().getSimpleName());
            parent = parent.getParent();
        }
        return value.toString();
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
            UUID.randomUUID(),
            "headless-panel-stress",
            "A Very Long Character Display Name For Layout Stress",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2025-01-01T00:00:00Z"),
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
