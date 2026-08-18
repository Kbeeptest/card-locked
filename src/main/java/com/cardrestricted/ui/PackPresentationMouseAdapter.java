package com.cardrestricted.ui;

import com.cardrestricted.presentation.PackPresentationAction;
import com.cardrestricted.presentation.PackPresentationSelection;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Objects;
import java.util.function.Consumer;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;

public final class PackPresentationMouseAdapter extends MouseAdapter
    implements MouseWheelListener
{
    private final PackPresentationOverlay overlay;
    private final Consumer<PackPresentationSelection> selectionHandler;

    public PackPresentationMouseAdapter(
        PackPresentationOverlay overlay,
        Consumer<PackPresentationSelection> selectionHandler)
    {
        this.overlay = Objects.requireNonNull(overlay, "overlay");
        this.selectionHandler = Objects.requireNonNull(
            selectionHandler,
            "selectionHandler");
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        if (!overlay.isActive())
        {
            return event;
        }
        overlay.setHoverPoint(event.getPoint());
        if (event.getButton() == MouseEvent.BUTTON1)
        {
            PackPresentationSelection selection =
                overlay.selectionAt(event.getPoint());
            if (selection.getAction() != PackPresentationAction.NONE)
            {
                selectionHandler.accept(selection);
            }
        }
        event.consume();
        return event;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent event)
    {
        return consumeWhenActive(event);
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent event)
    {
        return consumeWhenActive(event);
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent event)
    {
        if (overlay.isActive())
        {
            overlay.setHoverPoint(event.getPoint());
            event.consume();
        }
        return event;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent event)
    {
        if (overlay.isActive())
        {
            overlay.setHoverPoint(event.getPoint());
            event.consume();
        }
        return event;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent event)
    {
        overlay.clearHoverPoint();
        return consumeWhenActive(event);
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent event)
    {
        if (overlay.isActive())
        {
            overlay.setHoverPoint(event.getPoint());
        }
        return consumeWhenActive(event);
    }

    @Override
    public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
    {
        if (overlay.isActive())
        {
            overlay.adjustZoom(event.getWheelRotation());
            event.consume();
        }
        return event;
    }

    private MouseEvent consumeWhenActive(MouseEvent event)
    {
        if (overlay.isActive())
        {
            event.consume();
        }
        return event;
    }
}
