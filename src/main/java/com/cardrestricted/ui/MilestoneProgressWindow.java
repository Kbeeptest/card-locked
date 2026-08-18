package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.progression.ProgressionMilestoneDefinition;
import com.cardrestricted.progression.ProgressionMilestonePolicy;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;

/** Compact battle-pass style collection progression track. */
final class MilestoneProgressWindow
{
    private static final Color BACKGROUND = CardUiTheme.BACKGROUND;
    private static final Color PANEL = CardUiTheme.SURFACE;
    private static final Color PANEL_ACTIVE = CardUiTheme.ACTIVE_SURFACE;
    private static final Color MUTED = CardUiTheme.MUTED_TEXT;
    private static final Color GOLD = CardUiTheme.GOLD;
    private static final Color COMPLETE = CardUiTheme.OWNED;
    private static final Color READY = new Color(235, 155, 52);
    private static final Color LOCKED = new Color(104, 108, 116);
    private static final Font TITLE_FONT = CardUiTheme.TITLE;
    private static final Font HEADING_FONT = CardUiTheme.HEADING;
    private static final Font BODY_FONT = CardUiTheme.BODY;
    private static final Font META_FONT = CardUiTheme.META_BOLD;
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.UK);

    private final CardCatalogue catalogue;
    private final Runnable openStoreAction;
    private final JFrame frame = new JFrame("Card Locked Progression Track");
    private final JLabel countLabel = new JLabel();
    private final JLabel nextLabel = new JLabel();
    private final JProgressBar overallProgress = new JProgressBar(
        0,
        ProgressionMilestonePolicy.finalTrackThreshold());
    private final WidthTrackingPanel trackPanel = new WidthTrackingPanel();
    private CollectionState state;

    MilestoneProgressWindow(CardCatalogue catalogue, Runnable openStoreAction)
    {
        this.catalogue = java.util.Objects.requireNonNull(catalogue, "catalogue");
        this.openStoreAction = java.util.Objects.requireNonNull(
            openStoreAction,
            "openStoreAction");
        buildWindow();
    }

    void show(CollectionState newState)
    {
        update(newState);
        frame.setVisible(true);
        frame.toFront();
    }

    void update(CollectionState newState)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> update(newState));
            return;
        }
        state = newState;
        rebuildTrack();
    }

    void close()
    {
        frame.dispose();
    }

    private void buildWindow()
    {
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(500, 500));
        frame.setSize(new Dimension(640, 680));
        frame.setLocationByPlatform(true);
        frame.getContentPane().setBackground(BACKGROUND);
        frame.setLayout(new BorderLayout());

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(new Color(39, 33, 23));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, GOLD.darker()),
            BorderFactory.createEmptyBorder(12, 16, 11, 16)));

        JPanel titleRow = new JPanel(new BorderLayout(12, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("COLLECTION PROGRESSION");
        title.setForeground(new Color(255, 199, 84));
        title.setFont(TITLE_FONT);
        titleRow.add(title, BorderLayout.WEST);

        JButton store = new JButton("Open Store");
        store.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
        store.setFocusable(false);
        store.getAccessibleContext().setAccessibleDescription(
            "Return to the Card Locked sidebar and open the Store tab.");
        store.addActionListener(event -> {
            frame.setVisible(false);
            openStoreAction.run();
        });
        titleRow.add(store, BorderLayout.EAST);
        header.add(titleRow);
        header.add(Box.createRigidArea(new Dimension(0, 6)));

        JPanel progressText = new JPanel(new BorderLayout(10, 0));
        progressText.setOpaque(false);
        progressText.setAlignmentX(Component.LEFT_ALIGNMENT);
        countLabel.setForeground(Color.WHITE);
        countLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        progressText.add(countLabel, BorderLayout.WEST);
        nextLabel.setForeground(new Color(221, 202, 164));
        nextLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
        nextLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        progressText.add(nextLabel, BorderLayout.CENTER);
        header.add(progressText);
        header.add(Box.createRigidArea(new Dimension(0, 5)));

        overallProgress.setStringPainted(true);
        overallProgress.setFont(new Font(Font.DIALOG, Font.BOLD, 10));
        overallProgress.setForeground(GOLD);
        overallProgress.setBackground(new Color(29, 30, 33));
        overallProgress.setBorderPainted(false);
        overallProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
        overallProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        header.add(overallProgress);
        frame.add(header, BorderLayout.NORTH);

        trackPanel.setLayout(new BoxLayout(trackPanel, BoxLayout.Y_AXIS));
        trackPanel.setBackground(BACKGROUND);
        trackPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 14));
        JScrollPane scroll = new JScrollPane(trackPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.getViewport().setScrollMode(javax.swing.JViewport.SIMPLE_SCROLL_MODE);
        frame.add(scroll, BorderLayout.CENTER);

        rebuildTrack();
    }

    private void rebuildTrack()
    {
        int uniqueCards = state == null
            ? 0
            : ProgressionMilestonePolicy.uniqueOwnedCardCount(catalogue, state);
        int finalThreshold = ProgressionMilestonePolicy.finalTrackThreshold();
        int bounded = Math.min(uniqueCards, finalThreshold);
        countLabel.setText(NUMBER.format(uniqueCards) + " cards owned");
        overallProgress.setValue(bounded);
        overallProgress.setString(
            NUMBER.format(bounded) + " / " + NUMBER.format(finalThreshold));

        ProgressionMilestoneDefinition next = nextThreshold(uniqueCards);
        if (next == null)
        {
            nextLabel.setText("Track complete");
        }
        else
        {
            int remaining = Math.max(0, next.getRequiredCards() - uniqueCards);
            nextLabel.setText(
                "Next: " + next.getTitle() + " · "
                    + NUMBER.format(remaining) + " remaining");
        }

        trackPanel.removeAll();
        List<ProgressionMilestoneDefinition> milestones =
            ProgressionMilestonePolicy.track();
        for (int index = 0; index < milestones.size(); index++)
        {
            ProgressionMilestoneDefinition milestone = milestones.get(index);
            boolean nextThreshold = next == milestone;
            trackPanel.add(buildMilestoneRow(
                milestone,
                uniqueCards,
                index,
                milestones.size(),
                nextThreshold));
        }
        trackPanel.revalidate();
        trackPanel.repaint();
    }

    private JPanel buildMilestoneRow(
        ProgressionMilestoneDefinition milestone,
        int uniqueCards,
        int index,
        int total,
        boolean nextThreshold)
    {
        MilestoneState milestoneState = stateFor(milestone, uniqueCards);
        boolean highlighted = nextThreshold || milestoneState.readyToClaim;
        Color accent = milestoneState.accent;

        JPanel row = new CompactRowPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new TimelineNode(
            accent,
            milestoneState.complete,
            highlighted,
            index > 0,
            index + 1 < total), BorderLayout.WEST);

        JPanel card = new CompactRowPanel(new BorderLayout(9, 0));
        card.setOpaque(true);
        card.setBackground(highlighted ? PANEL_ACTIVE : PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
                highlighted ? GOLD : accent.darker(),
                highlighted ? 2 : 1),
            BorderFactory.createEmptyBorder(7, 8, 7, 8)));

        String iconPath = milestone.getKind()
            == ProgressionMilestoneDefinition.Kind.ONE_TIME_REWARD
                ? "/com/cardrestricted/ui/booster-sealed.png"
                : "/com/cardrestricted/ui/card-back.png";
        JLabel icon = new JLabel(CardUiAssets.icon(iconPath, 30, 45));
        icon.setVerticalAlignment(SwingConstants.TOP);
        card.add(icon, BorderLayout.WEST);

        JPanel content = new CompactRowPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel top = new CompactRowPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        JLabel title = new JLabel(milestone.getTitle());
        title.setForeground(Color.WHITE);
        title.setFont(HEADING_FONT);
        top.add(title, BorderLayout.CENTER);
        JLabel status = new JLabel(milestoneState.label, SwingConstants.RIGHT);
        status.setForeground(accent);
        status.setFont(META_FONT);
        top.add(status, BorderLayout.EAST);
        content.add(top);

        String thresholdText = milestone.getRequiredCards() == 0
            ? "START"
            : NUMBER.format(milestone.getRequiredCards()) + " CARDS";
        JLabel threshold = new JLabel(
            "TIER " + String.format(Locale.ROOT, "%02d", index + 1)
                + "  ·  " + thresholdText);
        threshold.setForeground(highlighted ? GOLD : accent);
        threshold.setFont(META_FONT);
        threshold.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(threshold);

        JTextArea summary = wrappedText(
            milestone.getRewardSummary(),
            new Font(Font.DIALOG, Font.BOLD, 11),
            new Color(225, 205, 164));
        summary.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        content.add(summary);

        JTextArea detail = wrappedText(
            milestone.getDetail(),
            BODY_FONT,
            MUTED);
        detail.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        content.add(detail);
        card.add(content, BorderLayout.CENTER);
        row.add(card, BorderLayout.CENTER);
        return row;
    }

    private MilestoneState stateFor(
        ProgressionMilestoneDefinition milestone,
        int uniqueCards)
    {
        boolean reached = uniqueCards >= milestone.getRequiredCards();
        if (milestone.getKind()
            == ProgressionMilestoneDefinition.Kind.ONE_TIME_REWARD)
        {
            boolean claimed = state != null
                && ProgressionMilestonePolicy.hasClaimed(
                    state,
                    milestone.getClaimedMarker());
            if (claimed)
            {
                return new MilestoneState("CLAIMED", COMPLETE, true, false);
            }
            if (reached)
            {
                return new MilestoneState(
                    "READY TO CLAIM",
                    READY,
                    false,
                    true);
            }
        }
        else if (reached)
        {
            return new MilestoneState("UNLOCKED", COMPLETE, true, false);
        }
        int remaining = Math.max(0, milestone.getRequiredCards() - uniqueCards);
        return new MilestoneState(
            "LOCKED · " + NUMBER.format(remaining) + " TO GO",
            LOCKED,
            false,
            false);
    }

    private ProgressionMilestoneDefinition nextThreshold(int uniqueCards)
    {
        for (ProgressionMilestoneDefinition milestone :
            ProgressionMilestonePolicy.track())
        {
            if (milestone.getRequiredCards() > uniqueCards)
            {
                return milestone;
            }
        }
        return null;
    }

    private static JTextArea wrappedText(String value, Font font, Color color)
    {
        JTextArea text = new WrappingTextArea(value);
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setFont(font);
        text.setForeground(color);
        text.setBorder(BorderFactory.createEmptyBorder());
        text.setRows(0);
        text.setColumns(1);
        return text;
    }

    private static final class MilestoneState
    {
        private final String label;
        private final Color accent;
        private final boolean complete;
        private final boolean readyToClaim;

        private MilestoneState(
            String label,
            Color accent,
            boolean complete,
            boolean readyToClaim)
        {
            this.label = label;
            this.accent = accent;
            this.complete = complete;
            this.readyToClaim = readyToClaim;
        }
    }

    private static final class TimelineNode extends JPanel
    {
        private static final long serialVersionUID = 1L;
        private final Color accent;
        private final boolean complete;
        private final boolean highlighted;
        private final boolean lineAbove;
        private final boolean lineBelow;

        private TimelineNode(
            Color accent,
            boolean complete,
            boolean highlighted,
            boolean lineAbove,
            boolean lineBelow)
        {
            this.accent = accent;
            this.complete = complete;
            this.highlighted = highlighted;
            this.lineAbove = lineAbove;
            this.lineBelow = lineBelow;
            setOpaque(false);
            Dimension size = new Dimension(28, 82);
            setMinimumSize(size);
            setPreferredSize(size);
            setMaximumSize(new Dimension(28, Integer.MAX_VALUE));
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
                int x = getWidth() / 2;
                int y = Math.min(28, getHeight() / 2);
                g.setStroke(new BasicStroke(2f));
                g.setColor(new Color(73, 75, 80));
                if (lineAbove)
                {
                    g.drawLine(x, 0, x, y - 8);
                }
                if (lineBelow)
                {
                    g.drawLine(x, y + 8, x, getHeight());
                }
                int radius = highlighted ? 8 : 7;
                g.setColor(highlighted ? GOLD : accent);
                g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
                g.setColor(BACKGROUND);
                int inner = complete ? 3 : 5;
                g.fillOval(x - inner, y - inner, inner * 2, inner * 2);
                if (complete)
                {
                    g.setColor(accent.brighter());
                    g.fillOval(x - 2, y - 2, 4, 4);
                }
            }
            finally
            {
                g.dispose();
            }
        }
    }

    private static final class CompactRowPanel extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private CompactRowPanel()
        {
            super();
        }

        private CompactRowPanel(java.awt.LayoutManager layout)
        {
            super(layout);
        }

        @Override
        public Dimension getMaximumSize()
        {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }
    }

    private static final class WrappingTextArea extends JTextArea
    {
        private static final long serialVersionUID = 1L;

        private WrappingTextArea(String value)
        {
            super(value);
            setColumns(1);
        }

        @Override
        public Dimension getPreferredSize()
        {
            int width = getParent() == null
                ? 380
                : Math.max(160, getParent().getWidth());
            setSize(new Dimension(width, Short.MAX_VALUE));
            Dimension preferred = super.getPreferredSize();
            return new Dimension(width, preferred.height);
        }

        @Override
        public Dimension getMaximumSize()
        {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private static final class WidthTrackingPanel extends JPanel implements Scrollable
    {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
            java.awt.Rectangle visibleRect,
            int orientation,
            int direction)
        {
            return 22;
        }

        @Override
        public int getScrollableBlockIncrement(
            java.awt.Rectangle visibleRect,
            int orientation,
            int direction)
        {
            return Math.max(90, visibleRect.height - 50);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
}
