package space.graynk.sie.gui;

import javafx.beans.DefaultProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;

@DefaultProperty("target")
public class ZoomableScrollPane extends ScrollPane {
    public double scaleValue = 1;
    private Node target;
    private Node zoomNode;

    private Node wrapNode(Node node) {
        Node outerNode = new StackPane(node);

        outerNode.addEventHandler(MouseEvent.ANY, event -> {
            if(event.getButton() != MouseButton.MIDDLE) event.consume();
        });


        outerNode.addEventFilter(ScrollEvent.ANY, e -> {
            if (!e.isControlDown()) {
                return;
            }
            e.consume();
            var delta = e.getDeltaY();
            if (delta == 0) return;
            onScroll(delta, new Point2D(e.getX(), e.getY()));
        });
        return outerNode;
    }

    public void resetScale() {
        var bounds = target.getLayoutBounds();
        var availableWidth = this.getWidth();
        var availableHeight = this.getHeight();
        var canvasWidth = bounds.getWidth();
        var canvasHeight = bounds.getHeight();
        if (canvasHeight > availableHeight) {
            scaleValue = availableHeight / canvasHeight;
        }
        if (canvasWidth > availableWidth) {
            var value = availableWidth / canvasWidth;
            if (value < scaleValue) {
                scaleValue = value;
            }
        }
        this.updateScale();
    }

    private void updateScale() {
        target.setScaleX(scaleValue);
        target.setScaleY(scaleValue);
    }

    private void onScroll(double wheelDelta, Point2D mousePoint) {
        double zoomIntensity = 0.01;
        double zoomFactor = Math.exp(wheelDelta * zoomIntensity);

        Bounds innerBounds = zoomNode.getLayoutBounds();
        Bounds viewportBounds = getViewportBounds();

        // calculate pixel offsets from [0, 1] range
        double valX = this.getHvalue() * (innerBounds.getWidth() - viewportBounds.getWidth());
        double valY = this.getVvalue() * (innerBounds.getHeight() - viewportBounds.getHeight());

        scaleValue = scaleValue * zoomFactor;
        updateScale();
        this.layout(); // refresh ScrollPane scroll positions & target bounds

        // convert target coordinates to zoomTarget coordinates
        Point2D posInZoomTarget = target.parentToLocal(zoomNode.parentToLocal(mousePoint));

        // calculate adjustment of scroll position (pixels)
        Point2D adjustment = target.getLocalToParentTransform().deltaTransform(posInZoomTarget.multiply(zoomFactor - 1));

        // convert back to [0, 1] range
        // (too large/small values are automatically corrected by ScrollPane)
        Bounds updatedInnerBounds = zoomNode.getBoundsInLocal();
        this.setHvalue((valX + adjustment.getX()) / (updatedInnerBounds.getWidth() - viewportBounds.getWidth()));
        this.setVvalue((valY + adjustment.getY()) / (updatedInnerBounds.getHeight() - viewportBounds.getHeight()));
    }

    // Can't override setContent, so let's do this
    public void setTarget(Node target) {
        this.target = target;
        this.zoomNode = new Group(target);
        super.setContent(wrapNode(zoomNode));
//        updateScale();
    }

    public Node getTarget() {
        return super.getContent();
    }
}
