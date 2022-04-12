package space.graynk.sie;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Screen;
import space.graynk.sie.gui.LayerCell;
import space.graynk.sie.gui.Layer;
import space.graynk.sie.gui.ToolButton;
import space.graynk.sie.gui.ZoomableScrollPane;
import space.graynk.sie.tools.Tool;
import space.graynk.sie.tools.drawing.DrawingTool;

import java.awt.image.BufferedImage;
import java.util.HashMap;

public class TabInternalsController {
    @FXML
    private ToggleGroup tools;
    @FXML
    private ColorPicker colorPicker;
    @FXML
    private ListView<Layer> layers;
    @FXML
    private ZoomableScrollPane scrollPane;
    @FXML
    private StackPane stackPane;
    @FXML
    private Canvas transparencyCanvas;
    @FXML
    private Canvas backgroundCanvas;
    @FXML
    private Canvas toolCanvas;
    @FXML
    private Spinner<Integer> brushSizeSpinner;

    private SelectionModel<Layer> selectionModel;
    private ReadOnlyObjectProperty<Layer> activeLayer;
    private ObjectProperty<Color> activeColor;
    private ObjectProperty<Tool> activeTool;
    public ReadOnlyBooleanProperty backgroundSelected;
    private final IntegerProperty layersCount = new SimpleIntegerProperty(0);
    public ReadOnlyIntegerProperty layersCountProperty;

    @FXML
    private void initialize() {
        brushSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 55, 4));
        transparencyCanvas.widthProperty().bind(backgroundCanvas.widthProperty());
        transparencyCanvas.heightProperty().bind(backgroundCanvas.heightProperty());
        var transparencyRedrawHandler = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                drawTransparencyCheckerboard();
            }
        };
        // TODO: double redraw is awful
        transparencyCanvas.widthProperty().addListener(transparencyRedrawHandler);
        transparencyCanvas.heightProperty().addListener(transparencyRedrawHandler);
        var layerCountWrapper = new ReadOnlyIntegerWrapper(0);
        layerCountWrapper.bind(layersCount);
        layersCountProperty = layerCountWrapper.getReadOnlyProperty();
        selectionModel = layers.getSelectionModel();
        var backgroundSelectedWrapper = new ReadOnlyBooleanWrapper();
        backgroundSelectedWrapper.bind(
                selectionModel.selectedIndexProperty()
                        .isEqualTo(
                                layersCountProperty.subtract(1)
                        )
        );
        backgroundSelected = backgroundSelectedWrapper.getReadOnlyProperty();
        activeLayer = selectionModel.selectedItemProperty();
        activeColor = colorPicker.valueProperty();

        layers.getItems().addListener((ListChangeListener<Layer>) c -> layersCount.set(layers.getItems().size()));
        layers.getItems().add(new Layer("Background", backgroundCanvas));
        selectionModel.selectFirst();
        layers.setCellFactory(listView -> new LayerCell());
    }

    private Screen getCurrentScreen() {
        var scene = transparencyCanvas.getScene();
        // on initialize Scene is null, but it will launch on primary screen anyway
        if (scene == null) {
            return Screen.getPrimary();
        }

        var window = transparencyCanvas.getScene().getWindow();
        return Screen.getScreensForRectangle(
                window.getX(),
                window.getY(),
                window.getWidth(),
                window.getHeight()
        ).get(0);
    }

    private void drawTransparencyCheckerboard() {
        var screen = getCurrentScreen();
        var width = transparencyCanvas.getWidth();
        var height = transparencyCanvas.getHeight();
        var squareSize = (int) Math.round(screen.getBounds().getWidth() * 0.01);
        var context = transparencyCanvas.getGraphicsContext2D();
        context.setFill(Color.WHITE);
        context.fillRect(0, 0, width, height);
        context.setFill(new Color(0.75, 0.75, 0.75, 1));
        // TODO: This should be a shader tbh, but JSL is a pain to set up and not documented at all
        for (var x = 0; x < width; x += squareSize) {
            var isOddRow = x % (2*squareSize) != 0;
            for (var y = isOddRow ? squareSize : 0 ; y < height; y += squareSize * 2) {
                context.fillRect(x, y, squareSize, squareSize);
            }
        }
    }

    public void newImage(boolean transparent) {
        this.newImage(500, 500, transparent);
    }

    public void newImage(int width, int height, boolean transparent) {
        backgroundCanvas.setWidth(width);
        backgroundCanvas.setHeight(height);
        var context = backgroundCanvas.getGraphicsContext2D();
//        context.setImageSmoothing(false);
        if (!transparent) {
            context.setFill(Color.WHITE);
            context.fillRect(0, 0, width, height);
        }
        this.activeLayer.getValue().updatePreview();
    }

    public void drawImage(Image image) {
        activeLayer.getValue().drawImage(image);
        Platform.runLater(() -> {
            layers.getItems().clear();
            layers.getItems().add(new Layer("Background", backgroundCanvas));
            selectionModel.selectFirst();
        });
    }

    @FXML
    private void onMousePressed(MouseEvent event) {
        // TODO do it with property binding and invalidation listeners
        var tool = ((ToolButton)tools.getSelectedToggle()).getTool();
        if (tool instanceof DrawingTool) {
            ((DrawingTool) tool).setRadius(brushSizeSpinner.getValue());
        }
        var canvas = activeLayer.getValue().getCanvas();
        canvas.getGraphicsContext2D().setFill(activeColor.getValue());
        canvas.getGraphicsContext2D().setStroke(activeColor.getValue());
        tool.handleDragStart(event, canvas);
    }

    @FXML
    private void onMouseDragged(MouseEvent event) {
        var tool = ((ToolButton)tools.getSelectedToggle()).getTool();
        tool.handleDrag(event);
    }

    @FXML
    private void onMouseReleased(MouseEvent event) {
        var tool = ((ToolButton)tools.getSelectedToggle()).getTool();
        tool.handleDragEnd(event);
        activeLayer.getValue().updatePreview();
    }

    @FXML
    private void onToolEntered(MouseEvent event) {
        var tool = ((ToolButton)tools.getSelectedToggle()).getTool();
        if (tool instanceof DrawingTool) {
            ((DrawingTool) tool).setRadius(brushSizeSpinner.getValue());
        }
        tool.handleToolEnter(event, toolCanvas);
    }

    @FXML
    private void onToolExited(MouseEvent event) {
        var tool = ((ToolButton)tools.getSelectedToggle()).getTool();
        tool.handleToolLeave(event, toolCanvas);
    }

    public Image getImage() {
        final SnapshotParameters spa = new SnapshotParameters();
        var scale = 1 / scrollPane.scaleValue;
        spa.setTransform(Transform.scale(scale, scale));
        spa.setFill(Color.TRANSPARENT);
        var image = new WritableImage((int) stackPane.getWidth(), (int) stackPane.getHeight());
        stackPane.snapshot(spa, image);
        return image;
    }

    public BufferedImage getImageForSaving() {
        return SwingFXUtils.fromFXImage(getImage(), null);
    }

    public void addLayer() {
        var layer = new Layer(String.format("Layer %d", layers.getItems().size()));
        layers.getItems().add(0, layer);
        selectionModel.selectFirst();
        stackPane.getChildren().add(layer.getCanvas());
    }

    public void deleteActiveLayer() {
        var selectedIndex = layers.getSelectionModel().getSelectedIndex();
        if (selectedIndex == layers.getItems().size() - 1) return;
        if (selectedIndex == 0) selectionModel.selectNext();
        else selectionModel.selectPrevious();
        layers.getItems().remove(selectedIndex);
        var canvases = stackPane.getChildren();
        canvases.remove(canvases.size() - selectedIndex - 1);
    }

    public void mergeDownActiveLayer() {
        var selectedIndex = selectionModel.getSelectedIndex();
        if (selectedIndex == layers.getItems().size() - 1) return;
        var image = activeLayer.getValue().getImage();
        var prevLayer = layers.getItems().get(selectedIndex+1);
        prevLayer.drawImage(image);
        this.deleteActiveLayer();
    }

    public void onKeyPressed(KeyEvent keyEvent) {
        if (!keyEvent.isControlDown()) return;
        if (keyEvent.getCode() == KeyCode.V && Clipboard.getSystemClipboard().hasImage()) {
            var image = Clipboard.getSystemClipboard().getImage();
            if (image == null) return;
            activeLayer.getValue().drawImage(image);
        } else if (keyEvent.getCode() == KeyCode.C) {
            var map = new HashMap<DataFormat, Object>();
            map.put(DataFormat.IMAGE, getImage());
            // TODO log failed attempts
            var result = Clipboard.getSystemClipboard().setContent(map);
        }
    }
}
